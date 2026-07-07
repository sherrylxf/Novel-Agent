package cn.bugstack.novel.domain.service.prompt;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.StoryContextBundle;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.plot.StoryPacingPolicy;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 将无序上下文整理为可复述的五层用户结构 + System 全局设定，供 {@link cn.bugstack.novel.domain.service.llm.ILLMClient#callWithTemplate} 使用。
 */
public final class SceneStructuredPromptBuilder {

    private static final String USER_TEMPLATE = """
            {novelAnchor}【任务指令】
            {instruction}

            【记忆上下文｜压缩历史】
            {memoryContext}

            【RAG与结构化知识｜事实依据】
            {ragContext}

            【规则约束】
            1) 人物行为须与「人物与地点约束」及全书锚点一致，禁止与已给设定冲突的胡编。
            2) 禁止凭空引入梗概未铺垫的重大设定；合理呼应「相关剧情线程」中的伏笔线索。
            3) 上述检索与摘要仅供事实参考，请转化为情节推演，勿整段复述。
            4) 若提供上一章结尾，开篇须时空、情绪连贯，忌硬切场景。
            5) 输出纯正文，禁止 Markdown 标题与小标题行。
            6) 情绪与节奏（网文可读性）：本章须有清晰的起伏，禁止「事件说明文」式平顺到底。全章至少安排 2～3 次张力节拍（如：侥幸—落空、误判—打脸、压抑—爆发、喘息—更大的迫近），在危机、动作、对话、短促独处反思之间交替推进。
            7) 人物内化：写出主角当下具体的情绪与身体反应（心悸、发冷、喉咙发紧等），允许短暂的错误判断或自我安慰后再被现实击碎；避免「全程冷静解说」的人设平面化。
            8) 微观钩子：在若干段末或场景转折处留下未答问题、反差细节、倒计时、见不得光的威胁或一条让读者心里咯噔一下的信息；禁止滥用套路套话（如「且听下回分解」）。
            9) 句法张弛：高压处多用短句、强动词与感官描写；缓和处可略加长句，形成快慢对比。关键对抗须有对话或交锋式独白，勿用大段旁白代替戏剧瞬间。
            10) 章末收束：结尾须在情绪或信息上「拧一下」——危机未解、代价显现、新威胁露头或认知颠覆中的至少一类，让读者有强烈动机接续阅读下一章。

            【风格参考｜Few-Shot（严禁照抄情节，仅学笔法、节奏与信息密度）】
            {fewShot}
            章节标题：{chapterTitle}
            章节梗概：{outline}
            关键人物：{keyCharacters}
            关键事件：{keyEvents}
            {previousChapterEnding}

            请根据以上分层信息生成章节正文（约{targetWords}字），直接以叙事开篇；须落实第 6～10 条的情绪曲线、钩子与章末拧转。
            """;

    private SceneStructuredPromptBuilder() {}

    public static SceneStructuredPrompt build(ChapterOutline outline,
                                              StoryContextBundle contextBundle,
                                              String previousChapterEnding,
                                              NovelContext context,
                                              int targetWords,
                                              ScenePromptOptions options) {
        ScenePromptOptions opt = options != null ? options : ScenePromptOptions.defaults();
        Object genre = context != null ? context.getAttribute("genre") : null;

        String systemPrompt = buildSystemPrompt(genre, targetWords);
        String novelAnchor = formatNovelAnchor(context) + formatPacingAnchor(context, outline);
        String instruction = buildInstruction(outline, targetWords);
        String memoryContext = nonEmpty(contextBundle != null ? contextBundle.getHistoryBackground() : null,
                "（当前无章节/场景摘要召回，主要依据梗概与 KG 约束。）");
        String ragContext = mergeRagSections(contextBundle);
        String fewShot = buildFewShotBlock(genre, opt);

        Map<String, Object> variables = new HashMap<>();
        variables.put("novelAnchor", novelAnchor);
        variables.put("instruction", instruction);
        variables.put("memoryContext", memoryContext);
        variables.put("ragContext", ragContext);
        variables.put("fewShot", fewShot);
        variables.put("chapterTitle", outline.getChapterTitle());
        variables.put("outline", outline.getOutline());
        variables.put("keyCharacters", outline.getKeyCharacters() != null
                ? String.join("、", outline.getKeyCharacters()) : "");
        variables.put("keyEvents", outline.getKeyEvents() != null
                ? String.join("、", outline.getKeyEvents()) : "");
        variables.put("targetWords", targetWords);
        variables.put("previousChapterEnding", (previousChapterEnding != null && !previousChapterEnding.isBlank())
                ? "【上一章结尾｜本章开篇须自然衔接】\n" + previousChapterEnding + "\n"
                : "");

        return new SceneStructuredPrompt(systemPrompt, USER_TEMPLATE, variables);
    }

    private static String buildSystemPrompt(Object genre, int targetWords) {
        String genreLine = GenrePromptProfile.systemGenreExtension(genre);
        return "你是一个专业的小说创作助手，擅长网文化叙事节奏与读者代入感。"
                + genreLine
                + " 全局要求：字数约" + targetWords + "字；优先服从用户消息中的【规则约束】与分层事实；"
                + "禁止 Markdown 章节标题；叙事须连贯可导出为 TXT。"
                + " 将「记忆上下文」作短期剧情连续性参考，「RAG与结构化知识」作事实锚点，二者均勿整段照抄。"
                + " 默认读者期待：每一千字左右应有一次情绪或信息上的「波动」，避免长时间同一情绪基调复述剧情。";
    }

    private static String formatNovelAnchor(NovelContext context) {
        if (context == null) {
            return "";
        }
        Object ws = context.getAttribute("worldSetting");
        Object cc = context.getAttribute("coreConflict");
        String w = ws != null ? ws.toString().trim() : "";
        String c = cc != null ? cc.toString().trim() : "";
        if (w.isEmpty() && c.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【全局设定｜全书锚点】\n");
        if (!c.isEmpty()) {
            sb.append("核心冲突：").append(StoryMemoryDocumentUtil.excerpt(c, 320)).append("\n");
        }
        if (!w.isEmpty()) {
            sb.append("世界观：").append(StoryMemoryDocumentUtil.excerpt(w, 320)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatPacingAnchor(NovelContext context, ChapterOutline outline) {
        if (context == null || outline == null) {
            return "";
        }
        VolumePlan volumePlan = context.getAttribute("currentVolume");
        String pacingBrief = StoryPacingPolicy.buildPacingBrief(context, volumePlan, outline.getChapterNumber());
        return "[Pacing]\n" + pacingBrief
                + "- Chapter ending rule: in CONVERGENCE/CLOSING/FINAL_RESOLUTION, prefer payoff over fresh cliffhanger.\n"
                + "- Final chapter rule: end with resolution, not a sequel hook.\n\n";
    }

    private static String buildInstruction(ChapterOutline outline, int targetWords) {
        String ch = outline.getChapterNumber() != null ? "第" + outline.getChapterNumber() + "章" : "本章";
        return "本轮目标：围绕下列梗概完成" + ch + "正文，推进主线与人物关系；冲突须「演出来」而非「讲出来」。\n"
                + "输出约 " + targetWords + " 字；描写、动作、对话穿插，避免说明书式设定堆砌；用具体场面承载世界观信息，忌连续多段纯说明。\n"
                + "结构上建议（可随梗概调整）：开篇尽快出现戏剧性时刻或悬念 → 若干次加压/释放的起伏 → 章末一次更强的认知或形势拧转。"
                + " 若梗概含多场景，可用空行分段，但仍禁止 Markdown 小标题。";
    }

    private static String mergeRagSections(StoryContextBundle bundle) {
        if (bundle == null) {
            return "（暂无 KG/RAG 注入块。）";
        }
        StringBuilder sb = new StringBuilder();
        appendSection(sb, bundle.getCharacterMemory());
        appendSection(sb, bundle.getActiveThreads());
        String merged = sb.toString().trim();
        return merged.isEmpty() ? "（暂无人物约束与剧情线程块，仅依梗概创作。）" : merged;
    }

    private static void appendSection(StringBuilder sb, String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(block.trim());
    }

    private static String buildFewShotBlock(Object genre, ScenePromptOptions opt) {
        if (!opt.isFewShotEnabled()) {
            return "（Few-Shot 已关闭，风格仅由 System、题材与 RAG 片段共同约束。）";
        }
        String key = GenrePromptProfile.resolveFewShotProfileKey(genre);
        return FewShotExampleCatalog.pick(key, opt.getMaxFewShotChars());
    }

    private static String nonEmpty(String value, String placeholder) {
        if (value == null || value.isBlank()) {
            return placeholder;
        }
        return value.trim();
    }
}
