package cn.bugstack.novel.domain.agent.impl.generation;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.StoryContextBundle;
import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.model.entity.StoryRetrievalQuery;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryContextBuilderService;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;
import cn.bugstack.novel.domain.service.rag.StoryQueryBuilderService;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.domain.service.plot.IPlotTrackerService;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 场景生成Agent
 * 根据章节梗概生成正文场景
 */
@Slf4j
@Component
public class SceneGenerationAgent extends AbstractAgent<ChapterOutline, Scene> {

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "([A-Za-z0-9\\u4e00-\\u9fa5]{2,24}(?:网吧包间|会议室|办公室|工作室|实验室|图书馆|操场|宿舍|教室|食堂|咖啡馆|酒吧|酒店|商场|公司|大厦|广场|车站|机场|小区|公寓|村口|镇上|校园|学校|包间|网吧|演武场|练功房|大殿|偏殿|后山|山谷|山洞|洞府|山门|宗门|书院|庭院|别院|王府|府邸|阁楼|楼顶|城门|城主府|街口|巷口|码头|渡口|客栈|茶馆|擂台|矿场|药园|秘境|遗迹|祭坛|平原|森林|密林|湖畔|河畔|海边|江边|宫殿|神殿|青云宗|极速网吧))");
    
    private final IRAGService ragService;
    private final IKnowledgeGraphService knowledgeGraphService;
    private final IPlotTrackerService plotTrackerService;
    private final ILLMClient llmClient;
    private final StoryQueryBuilderService queryBuilderService;
    private final StoryContextBuilderService contextBuilderService;
    
    @Autowired
    public SceneGenerationAgent(IRAGService ragService, ILLMClient llmClient) {
        this(ragService, null, null, llmClient, null, null);
    }

    public SceneGenerationAgent(IRAGService ragService, IKnowledgeGraphService knowledgeGraphService, IPlotTrackerService plotTrackerService, ILLMClient llmClient) {
        this(ragService, knowledgeGraphService, plotTrackerService, llmClient, null, null);
    }

    public SceneGenerationAgent(IRAGService ragService,
                                IKnowledgeGraphService knowledgeGraphService,
                                IPlotTrackerService plotTrackerService,
                                ILLMClient llmClient,
                                StoryQueryBuilderService queryBuilderService,
                                StoryContextBuilderService contextBuilderService) {
        super(AgentType.SCENE_GENERATION);
        this.ragService = ragService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.plotTrackerService = plotTrackerService;
        this.llmClient = llmClient;
        this.queryBuilderService = queryBuilderService;
        this.contextBuilderService = contextBuilderService;
    }
    
    @Override
    protected Scene doExecute(ChapterOutline outline, NovelContext context) {
        log.info("开始生成场景，章节: {}", outline.getChapterTitle());
        
        try {
            String novelId = context != null ? context.getNovelId() : null;
            List<String> knowledgeCharacters = collectKnowledgeCharacters(context, outline);
            StoryKnowledgeSnapshot knowledgeSnapshot = knowledgeGraphService != null
                    ? knowledgeGraphService.buildStoryKnowledge(
                            novelId,
                            knowledgeCharacters,
                            null,
                            outline.getKeyEvents(),
                            5)
                    : StoryKnowledgeSnapshot.builder().build();
            List<String> unresolvedForeshadowing = plotTrackerService != null
                    ? plotTrackerService.collectUnresolvedForeshadowing(novelId, outline.getForeshadowing())
                    : new ArrayList<>();

            StoryRetrievalQuery retrievalQuery = buildRetrievalQuery(outline, context, knowledgeSnapshot);
            StoryContextBundle contextBundle = buildStoryContext(outline, novelId, retrievalQuery, knowledgeSnapshot, unresolvedForeshadowing);
            if (context != null) {
                context.setAttribute("latestStoryRetrievalQuery", retrievalQuery);
                context.setAttribute("latestStoryContextBundle", contextBundle);
            }

            String content = generateContent(outline, contextBundle);
            
            // 提取场景类型和地点
            String sceneType = extractSceneType(content);
            String location = extractLocation(outline, content);
            
            Scene scene = Scene.builder()
                    .sceneId(UUID.randomUUID().toString())
                    .sceneNumber(1)
                    .sceneTitle(outline.getChapterTitle())
                    .sceneType(sceneType)
                    .content(content)
                    .wordCount(content.length())
                    .characters(outline.getKeyCharacters() != null ? 
                            outline.getKeyCharacters().toArray(new String[0]) : new String[0])
                    .location(location)
                    .build();
            
            log.info("场景生成完成，字数: {}", scene.getWordCount());
            return scene;
            
        } catch (Exception e) {
            log.error("生成场景失败，使用降级策略", e);
            return generateFallbackScene(outline);
        }
    }
    
    /**
     * 调用LLM生成场景正文（3000字左右）
     */
    private String generateContent(ChapterOutline outline, StoryContextBundle contextBundle) {
        if (llmClient == null) {
            return generateFallbackContent(outline);
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据章节梗概生成详细的场景正文。" +
                    "要求：1. 字数约3000字；2. 文笔流畅，情节生动；3. 严格遵守人物设定、世界规则和剧情线程；4. 优先参考结构化上下文，不直接复述历史文本。";
            
            String template = "章节标题：{chapterTitle}\n" +
                    "章节梗概：{outline}\n" +
                    "关键人物：{keyCharacters}\n" +
                    "关键事件：{keyEvents}\n" +
                    "{historyBackground}\n" +
                    "{characterMemory}\n" +
                    "{activeThreads}\n\n" +
                    "请根据以上信息生成详细的场景正文（约3000字），要求文笔流畅，情节生动，符合章节梗概，并确保前后设定一致。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("chapterTitle", outline.getChapterTitle());
            variables.put("outline", outline.getOutline());
            variables.put("keyCharacters", outline.getKeyCharacters() != null ? 
                    String.join("、", outline.getKeyCharacters()) : "");
            variables.put("keyEvents", outline.getKeyEvents() != null ? 
                    String.join("、", outline.getKeyEvents()) : "");
            variables.put("historyBackground", contextBundle != null ? contextBundle.getHistoryBackground() : "");
            variables.put("characterMemory", contextBundle != null ? contextBundle.getCharacterMemory() : "");
            variables.put("activeThreads", contextBundle != null ? contextBundle.getActiveThreads() : "");
            
            String content = llmClient.callWithTemplate(systemPrompt, template, variables);
            
            // 确保字数在合理范围内（2000-4000字）
            if (content.length() < 2000) {
                log.warn("生成内容字数不足，尝试补充");
                content = content + "\n\n" + generateAdditionalContent(outline);
            }
            
            return content;
            
        } catch (Exception e) {
            log.warn("调用LLM生成场景失败，使用降级策略", e);
            return generateFallbackContent(outline);
        }
    }

    private StoryRetrievalQuery buildRetrievalQuery(ChapterOutline outline,
                                                    NovelContext context,
                                                    StoryKnowledgeSnapshot knowledgeSnapshot) {
        if (queryBuilderService != null) {
            return queryBuilderService.buildSceneQuery(outline, context, knowledgeSnapshot);
        }
        return StoryRetrievalQuery.builder()
                .novelId(context != null ? context.getNovelId() : null)
                .queryText(StoryMemoryDocumentUtil.buildSearchQuery(outline))
                .memoryTypes(new ArrayList<>(List.of("chapter_summary", "scene_summary", "scene_fulltext")))
                .chapterTo(outline != null ? outline.getChapterNumber() : null)
                .topK(3)
                .build();
    }

    private StoryContextBundle buildStoryContext(ChapterOutline outline,
                                                 String novelId,
                                                 StoryRetrievalQuery retrievalQuery,
                                                 StoryKnowledgeSnapshot knowledgeSnapshot,
                                                 List<String> unresolvedForeshadowing) {
        if (contextBuilderService != null) {
            return contextBuilderService.buildSceneContext(retrievalQuery, knowledgeSnapshot, unresolvedForeshadowing);
        }

        List<String> memoryTypes = retrievalQuery != null && retrievalQuery.getMemoryTypes() != null
                ? retrievalQuery.getMemoryTypes()
                : List.of("chapter_summary", "scene_summary", "scene_fulltext");
        List<IRAGService.SearchResult> similarScenes;
        if (novelId != null && !novelId.isEmpty()) {
            Map<String, Object> filter = new HashMap<>();
            filter.put("novelId", novelId);
            filter.put("memoryType", memoryTypes);
            similarScenes = ragService.searchWithMetadataFilter(
                    retrievalQuery != null ? retrievalQuery.getQueryText() : StoryMemoryDocumentUtil.buildSearchQuery(outline),
                    "zh",
                    6,
                    filter);
            if (similarScenes.isEmpty()) {
                similarScenes = ragService.search(
                        retrievalQuery != null ? retrievalQuery.getQueryText() : StoryMemoryDocumentUtil.buildSearchQuery(outline),
                        "zh",
                        6,
                        novelId,
                        memoryTypes);
            }
        } else {
            similarScenes = ragService.search(
                    retrievalQuery != null ? retrievalQuery.getQueryText() : StoryMemoryDocumentUtil.buildSearchQuery(outline),
                    "zh",
                    6,
                    null,
                    memoryTypes);
        }
        return StoryContextBundle.builder()
                .historyBackground(formatSearchResults("历史背景", similarScenes))
                .characterMemory(formatList("人物与地点约束", knowledgeSnapshot != null ? knowledgeSnapshot.getCharacterFacts() : null))
                .activeThreads(formatList("相关剧情线程", unresolvedForeshadowing))
                .citedMemories(similarScenes)
                .build();
    }

    private String formatList(String title, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        List<String> validLines = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                validLines.add("- " + line.trim());
            }
        }
        if (validLines.isEmpty()) {
            return "";
        }
        return title + "：\n" + String.join("\n", validLines);
    }

    private String formatSearchResults(String title, List<IRAGService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (IRAGService.SearchResult result : results) {
            if (result == null || result.getContent() == null || result.getContent().trim().isEmpty()) {
                continue;
            }
            Object memoryType = result.getMetadata() != null ? result.getMetadata().get("memoryType") : "";
            lines.add("- [" + memoryType + "] " + StoryMemoryDocumentUtil.excerpt(result.getContent(), 180));
            if (lines.size() >= 4) {
                break;
            }
        }
        if (lines.isEmpty()) {
            return "";
        }
        return title + "：\n" + String.join("\n", lines);
    }

    private List<String> collectKnowledgeCharacters(NovelContext context, ChapterOutline outline) {
        LinkedHashSet<String> names = new LinkedHashSet<>(KgStorySyncUtil.distinctNonBlank(
                outline != null ? outline.getKeyCharacters() : null));
        if (outline != null && outline.getScenes() != null) {
            for (Scene scene : outline.getScenes()) {
                if (scene == null || scene.getCharacters() == null) {
                    continue;
                }
                names.addAll(KgStorySyncUtil.distinctNonBlank(List.of(scene.getCharacters())));
            }
        }
        VolumePlan currentVolume = context != null ? context.getAttribute("currentVolume") : null;
        if (currentVolume != null && currentVolume.getChapterOutlines() != null) {
            for (ChapterOutline chapter : currentVolume.getChapterOutlines()) {
                if (chapter == null || chapter == outline) {
                    continue;
                }
                names.addAll(KgStorySyncUtil.distinctNonBlank(chapter.getKeyCharacters()));
                if (chapter.getScenes() == null) {
                    continue;
                }
                for (Scene scene : chapter.getScenes()) {
                    if (scene == null || scene.getCharacters() == null) {
                        continue;
                    }
                    names.addAll(KgStorySyncUtil.distinctNonBlank(List.of(scene.getCharacters())));
                }
            }
        }
        return new ArrayList<>(names);
    }
    
    /**
     * 生成补充内容
     */
    private String generateAdditionalContent(ChapterOutline outline) {
        // 简单的补充内容生成
        return "故事继续发展，情节逐渐深入。主角在经历了一系列挑战后，获得了新的成长和突破。";
    }
    
    /**
     * 提取场景类型
     */
    private String extractSceneType(String content) {
        // 简单判断场景类型
        if (content.contains("对话") || content.contains("说") || content.contains("道")) {
            return "对话";
        } else if (content.contains("战斗") || content.contains("攻击") || content.contains("战斗")) {
            return "战斗";
        } else if (content.contains("修炼") || content.contains("突破")) {
            return "修炼";
        }
        return "日常";
    }
    
    /**
     * 提取地点
     */
    private String extractLocation(ChapterOutline outline, String content) {
        List<String> texts = new ArrayList<>();
        if (outline != null) {
            texts.add(outline.getChapterTitle());
            texts.add(outline.getOutline());
        }
        texts.add(content);

        for (String text : texts) {
            String candidate = findBestLocationCandidate(text);
            if (candidate != null) {
                return candidate;
            }
        }
        return "待确定";
    }

    private String findBestLocationCandidate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = LOCATION_PATTERN.matcher(text);
        String best = null;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!KgStorySyncUtil.hasMeaningfulText(candidate)) {
                continue;
            }
            String normalized = normalizeLocation(candidate);
            if (normalized == null) {
                continue;
            }
            if (best == null || normalized.length() > best.length()) {
                best = normalized;
            }
        }
        return best;
    }

    private String normalizeLocation(String raw) {
        if (raw == null) {
            return null;
        }
        String candidate = raw
                .replace("“", "")
                .replace("”", "")
                .replace("\"", "")
                .replace("《", "")
                .replace("》", "")
                .trim();
        if (candidate.length() < 2) {
            return null;
        }
        if ("待确定".equals(candidate) || candidate.endsWith("待完善")) {
            return null;
        }
        return candidate;
    }
    
    /**
     * 降级策略：生成基础场景
     */
    private Scene generateFallbackScene(ChapterOutline outline) {
        String content = generateFallbackContent(outline);
        return Scene.builder()
                .sceneId(UUID.randomUUID().toString())
                .sceneNumber(1)
                .sceneTitle(outline.getChapterTitle())
                .sceneType("日常")
                .content(content)
                .wordCount(content.length())
                .characters(outline.getKeyCharacters() != null ? 
                        outline.getKeyCharacters().toArray(new String[0]) : new String[0])
                .location("待确定")
                .build();
    }
    
    /**
     * 降级策略：生成基础内容
     */
    private String generateFallbackContent(ChapterOutline outline) {
        return String.format("【这是模拟生成的场景正文，实际应该调用LLM生成】\n\n" +
                "%s\n\n" +
                "根据章节梗概：%s\n" +
                "本章主要讲述了主角的成长历程，通过努力克服困难，最终获得突破。",
                outline.getChapterTitle(),
                outline.getOutline());
    }
}
