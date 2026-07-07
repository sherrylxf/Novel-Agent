package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.domain.service.plot.StoryPacingPolicy;
import cn.bugstack.novel.types.enums.AgentType;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 章节梗概Agent
 * 生成章节梗概，不写正文
 */
@Slf4j
@Component
public class ChapterOutlineAgent extends AbstractAgent<Object[], ChapterOutline> {
    
    private final ILLMClient llmClient;
    
    public ChapterOutlineAgent(ILLMClient llmClient) {
        super(AgentType.CHAPTER_OUTLINE);
        this.llmClient = llmClient;
    }
    
    @Override
    protected ChapterOutline doExecute(Object[] input, NovelContext context) {
        VolumePlan volumePlan = (VolumePlan) input[0];
        Integer chapterNumber = (Integer) input[1];
        
        log.info("开始生成章节梗概，章节序号: {}", chapterNumber);
        
        try {
            // 调用LLM生成章节梗概
            String pacingBrief = StoryPacingPolicy.buildPacingBrief(context, volumePlan, chapterNumber);
            String chapterTitle = generateChapterTitle(volumePlan, chapterNumber);
            String outline = generateOutline(volumePlan, chapterNumber, pacingBrief);
            List<String> keyCharacters = generateKeyCharacters(volumePlan, chapterNumber, outline);
            List<String> keyEvents = generateKeyEvents(volumePlan, chapterNumber);
            List<String> foreshadowing = generateForeshadowing(volumePlan, chapterNumber, outline, pacingBrief);
            
            ChapterOutline chapterOutline = ChapterOutline.builder()
                    .chapterId(UUID.randomUUID().toString())
                    .chapterNumber(chapterNumber)
                    .chapterTitle(chapterTitle)
                    .outline(outline)
                    .keyCharacters(keyCharacters)
                    .keyEvents(keyEvents)
                    .foreshadowing(foreshadowing)
                    .scenes(new ArrayList<>())
                    .build();
            
            log.info("章节梗概生成完成，章节ID: {}", chapterOutline.getChapterId());
            return chapterOutline;
            
        } catch (Exception e) {
            log.error("生成章节梗概失败，使用降级策略", e);
            return generateFallbackOutline(volumePlan, chapterNumber);
        }
    }
    
    /**
     * 调用LLM生成章节标题
     * 格式统一为「第X章：副标题」，副标题避免与已有章节重复高频词
     */
    private String generateChapterTitle(VolumePlan volumePlan, int chapterNumber) {
        if (llmClient == null) {
            return "第" + chapterNumber + "章";
        }

        List<String> previousTitles = collectPreviousChapterTitles(volumePlan, chapterNumber);

        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据卷主题和章节序号，生成章节副标题（不含「第X章」）。" +
                    "要求：1. 只输出副标题内容，10-20字；2. 格式为逗号或顿号分隔的短语，如「汴京烟火处，糖葫芦破局」；" +
                    "3. 【重要】若提供了已有章节标题，本章副标题不得与它们重复使用同一核心词（如已有「糖葫芦」则本章避免再用）。";
            String template = "卷主题：{volumeTheme}\n\n" +
                    "当前为第{chapterNumber}章。请生成本章副标题（10-20字），只输出副标题，不要写「第X章」。" +
                    "{previousTitlesHint}\n\n" +
                    "直接输出副标题，例如：汴京烟火处，初试身手";

            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            variables.put("previousTitlesHint", previousTitles.isEmpty()
                    ? ""
                    : "已有章节副标题（本章需避免与它们重复核心词）：" + String.join("；", previousTitles));

            String raw = llmClient.callWithTemplate(systemPrompt, template, variables);
            String subtitle = normalizeSubtitle(raw, chapterNumber);
            return "第" + chapterNumber + "章：" + subtitle;

        } catch (Exception e) {
            log.warn("生成章节标题失败，使用默认标题", e);
            return "第" + chapterNumber + "章";
        }
    }

    private List<String> collectPreviousChapterTitles(VolumePlan volumePlan, int currentChapter) {
        List<String> titles = new ArrayList<>();
        if (volumePlan == null || volumePlan.getChapterOutlines() == null) {
            return titles;
        }
        for (ChapterOutline co : volumePlan.getChapterOutlines()) {
            if (co != null && co.getChapterNumber() != null && co.getChapterNumber() < currentChapter
                    && co.getChapterTitle() != null && !co.getChapterTitle().isBlank()) {
                String t = stripChapterPrefix(co.getChapterTitle().trim());
                if (!t.isEmpty()) {
                    titles.add(t);
                }
            }
        }
        return titles;
    }

    private String stripChapterPrefix(String title) {
        if (title == null || title.isBlank()) return "";
        String s = title.replaceFirst("^第[一二三四五六七八九十百千\\d]+章[：:]\\s*", "").trim();
        return s.isEmpty() ? title.trim() : s;
    }

    private String normalizeSubtitle(String raw, int chapterNumber) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.trim().replace("\n", " ").replaceAll("\\s+", " ");
        s = stripChapterPrefix(s);
        if (s.length() > 50) s = s.substring(0, 50);
        return s;
    }
    
    /**
     * 调用LLM生成章节梗概
     */
    private String generateOutline(VolumePlan volumePlan, int chapterNumber, String pacingBrief) {
        if (llmClient == null) {
            return buildGenericOutline(volumePlan, chapterNumber);
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据卷主题和章节序号，生成本章章节梗概（220-380字）。"
                    + "梗概须具备「可写成起伏正文」的骨架：至少写明 2～3 个情绪/张力节拍（例如希望—落空、误判—打脸、喘息—更大危机），"
                    + "并写出主角此刻核心的内心矛盾或恐惧；章末须保留一个让读者想追读的悬念或利害关系。"
                    + "避免只有事件列表而无心理冲击；世界观信息须揉进具体场面，不要列设定清单。";
            String template = "卷主题：{volumeTheme}\n\n请为第{chapterNumber}章生成章节梗概，必须包括：\n"
                    + "（1）开篇如何抓住读者：戏剧性时刻、悬念或反常情况；\n"
                    + "（2）中段 2～3 次加压或反转的节奏点（可简短标注情绪变化）；\n"
                    + "（3）结尾未解决的钩子或代价（谁受伤了、秘密露头、倒计时、新规则反噬等）。\n"
                    + "用自然段落写出，不要列 Markdown 小标题。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            variables.put("pacingBrief", pacingBrief != null ? pacingBrief : "");
            template = template + "\n\n{pacingBrief}\n"
                    + "Apply the pacing contract strictly. If this chapter is in CONVERGENCE/CLOSING/FINAL_RESOLUTION, "
                    + "the outline must resolve or narrow existing hooks instead of expanding the plot.\n";
            
            return llmClient.callWithTemplate(systemPrompt, template, variables);
            
        } catch (Exception e) {
            log.warn("生成章节梗概失败，使用默认梗概", e);
            return buildGenericOutline(volumePlan, chapterNumber);
        }
    }
    
    /**
     * 调用LLM提取关键人物（基于章节梗概，确保主角、配角、势力代表等均被纳入）
     */
    private List<String> generateKeyCharacters(VolumePlan volumePlan, int chapterNumber, String outline) {
        if (llmClient == null) {
            return Arrays.asList("主角", "配角");
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据章节梗概，提取本章出现的所有关键人物（含主角、配角、势力代表、对手、盟友等），以JSON数组格式返回。务必列举具体人名，不要用\"主角\"\"配角\"等泛称。";
            String template = "卷主题：{volumeTheme}\n章节序号：{chapterNumber}\n章节梗概：{outline}\n\n请从梗概中提取本章登场的所有人物（具体人名），以JSON数组返回，例如：[\"林衍\",\"艾薇\",\"星盟指挥官\"]。至少包含主角，若有其他势力、对手、盟友等也需列入。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            variables.put("outline", outline != null ? outline : "");
            
            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            // 提取JSON数组
            String jsonArray = extractJsonArray(response);
            return JSON.parseArray(jsonArray, String.class);
            
        } catch (Exception e) {
            log.warn("提取关键人物失败，使用默认人物", e);
            return Arrays.asList("主角", "配角");
        }
    }
    
    /**
     * 调用LLM提取关键事件
     */
    private List<String> generateKeyEvents(VolumePlan volumePlan, int chapterNumber) {
        if (llmClient == null) {
            return Arrays.asList("情节推进", "面临挑战", "获得成长");
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据章节梗概，提取关键事件列表，以JSON数组格式返回。";
            String template = "卷主题：{volumeTheme}\n章节序号：{chapterNumber}\n\n请提取本章的关键事件列表，以JSON数组格式返回，例如：[\"事件1\",\"事件2\"]";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            
            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            // 提取JSON数组
            String jsonArray = extractJsonArray(response);
            return JSON.parseArray(jsonArray, String.class);
            
        } catch (Exception e) {
            log.warn("提取关键事件失败，使用默认事件", e);
            return Arrays.asList("情节推进", "面临挑战", "获得成长");
        }
    }

    /**
     * 生成本章引入或推进的伏笔/剧情线程。
     */
    private List<String> generateForeshadowing(VolumePlan volumePlan, int chapterNumber, String outline, String pacingBrief) {
        if (llmClient == null) {
            return new ArrayList<>();
        }

        try {
            String systemPrompt = "你是一个专业的小说策划编辑。请根据章节梗概，提取本章新增或推进的伏笔/剧情线程，以 JSON 数组格式返回。";
            String template = "卷主题：{volumeTheme}\n章节序号：{chapterNumber}\n章节梗概：{outline}\n\n" +
                    "请提取 0-3 个需要在后续章节持续追踪的伏笔或剧情线程，返回 JSON 数组，例如：[\"主角身世线索\",\"宗门试炼背后的阴谋\"]。";

            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            variables.put("outline", outline);
            variables.put("pacingBrief", pacingBrief != null ? pacingBrief : "");
            template = template + "\n\n{pacingBrief}\n"
                    + "Foreshadowing rule: return only hooks that fit the remaining chapter budget. "
                    + "In CONVERGENCE return 0-2 payoff/closure threads; in CLOSING or FINAL_RESOLUTION return [] unless it is a short-lived hook resolved immediately.\n";

            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            String jsonArray = extractJsonArray(response);
            return JSON.parseArray(jsonArray, String.class);
        } catch (Exception e) {
            log.warn("提取伏笔失败，使用空列表", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 从响应中提取JSON数组
     */
    private String extractJsonArray(String response) {
        int start = response.indexOf("[");
        int end = response.lastIndexOf("]");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "[]";
    }
    
    /**
     * 构建通用降级梗概（题材无关，避免修仙等固定文案）
     */
    private String buildGenericOutline(VolumePlan volumePlan, int chapterNumber) {
        String themeHint = "";
        if (volumePlan != null && volumePlan.getVolumeTheme() != null && !volumePlan.getVolumeTheme().isBlank()) {
            String t = volumePlan.getVolumeTheme().trim().replaceAll("\\s+", " ");
            themeHint = t.length() > 60 ? t.substring(0, 60) + "…" : t;
        }
        if (themeHint.isEmpty()) {
            return String.format("第%d章梗概：根据卷主题展开情节，主角面临挑战并推进成长。", chapterNumber);
        }
        return String.format("第%d章梗概：%s 本章将围绕该主题展开关键情节。", chapterNumber, themeHint);
    }

    /**
     * 降级策略
     */
    private ChapterOutline generateFallbackOutline(VolumePlan volumePlan, int chapterNumber) {
        return ChapterOutline.builder()
                .chapterId(UUID.randomUUID().toString())
                .chapterNumber(chapterNumber)
                .chapterTitle("第" + chapterNumber + "章")
                .outline(buildGenericOutline(volumePlan, chapterNumber))
                .keyCharacters(Arrays.asList("主角", "配角"))
                .keyEvents(Arrays.asList("情节推进", "面临挑战", "获得成长"))
                .foreshadowing(new ArrayList<>())
                .scenes(new ArrayList<>())
                .build();
    }
}
