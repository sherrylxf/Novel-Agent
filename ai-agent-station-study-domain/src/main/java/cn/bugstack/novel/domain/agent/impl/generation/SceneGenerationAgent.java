package cn.bugstack.novel.domain.agent.impl.generation;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.domain.service.plot.IPlotTrackerService;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 场景生成Agent
 * 根据章节梗概生成正文场景
 */
@Slf4j
@Component
public class SceneGenerationAgent extends AbstractAgent<ChapterOutline, Scene> {
    
    private final IRAGService ragService;
    private final IKnowledgeGraphService knowledgeGraphService;
    private final IPlotTrackerService plotTrackerService;
    private final ILLMClient llmClient;
    
    @Autowired
    public SceneGenerationAgent(IRAGService ragService, ILLMClient llmClient) {
        this(ragService, null, null, llmClient);
    }

    public SceneGenerationAgent(IRAGService ragService, IKnowledgeGraphService knowledgeGraphService, IPlotTrackerService plotTrackerService, ILLMClient llmClient) {
        super(AgentType.SCENE_GENERATION);
        this.ragService = ragService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.plotTrackerService = plotTrackerService;
        this.llmClient = llmClient;
    }
    
    @Override
    protected Scene doExecute(ChapterOutline outline, NovelContext context) {
        log.info("开始生成场景，章节: {}", outline.getChapterTitle());
        
        try {
            // 从 RAG 检索章节级/场景级记忆，而不是只拿相似正文。
            String novelId = context != null ? context.getNovelId() : null;
            String searchQuery = StoryMemoryDocumentUtil.buildSearchQuery(outline);
            List<String> memoryTypes = List.of("chapter_summary", "scene_summary", "scene_fulltext");
            List<IRAGService.SearchResult> similarScenes;
            if (novelId != null && !novelId.isEmpty()) {
                Map<String, Object> filter = new HashMap<>();
                filter.put("novelId", novelId);
                filter.put("memoryType", memoryTypes);
                similarScenes = ragService.searchWithMetadataFilter(searchQuery, "zh", 6, filter);
                if (similarScenes.isEmpty()) {
                    similarScenes = ragService.search(searchQuery, "zh", 6, novelId, memoryTypes);
                }
            } else {
                similarScenes = ragService.search(searchQuery, "zh", 6, null, memoryTypes);
            }
            StoryKnowledgeSnapshot knowledgeSnapshot = knowledgeGraphService != null
                    ? knowledgeGraphService.buildStoryKnowledge(
                            novelId,
                            outline.getKeyCharacters(),
                            null,
                            outline.getKeyEvents(),
                            5)
                    : StoryKnowledgeSnapshot.builder().build();
            List<String> unresolvedForeshadowing = plotTrackerService != null
                    ? plotTrackerService.collectUnresolvedForeshadowing(novelId, outline.getForeshadowing())
                    : new ArrayList<>();
            
            // 调用LLM生成场景正文
            String content = generateContent(outline, similarScenes, knowledgeSnapshot, unresolvedForeshadowing);
            
            // 提取场景类型和地点
            String sceneType = extractSceneType(content);
            String location = extractLocation(content);
            
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
    private String generateContent(
            ChapterOutline outline,
            List<IRAGService.SearchResult> similarScenes,
            StoryKnowledgeSnapshot knowledgeSnapshot,
            List<String> unresolvedForeshadowing) {
        if (llmClient == null) {
            return generateFallbackContent(outline);
        }
        
        try {
            // 构建RAG参考内容
            StringBuilder ragContext = new StringBuilder();
            if (!similarScenes.isEmpty()) {
                ragContext.append("\n相关剧情记忆：\n");
                for (var result : similarScenes) {
                    Object memoryType = result.getMetadata() != null ? result.getMetadata().get("memoryType") : "";
                    String excerpt = result.getContent().substring(0,
                            Math.min(300, result.getContent().length()));
                    ragContext.append("[").append(memoryType).append("] ");
                    ragContext.append(excerpt).append("\n---\n");
                }
            }

            String worldContext = formatList("世界设定", knowledgeSnapshot != null ? knowledgeSnapshot.getWorldFacts() : null);
            String characterContext = formatList("人物关系与设定", knowledgeSnapshot != null ? knowledgeSnapshot.getCharacterFacts() : null);
            String plotContext = formatList("活跃剧情线程", knowledgeSnapshot != null ? knowledgeSnapshot.getActivePlotThreads() : null);
            String unresolvedContext = formatList("未回收伏笔", unresolvedForeshadowing);
            
            String systemPrompt = "你是一个专业的小说创作助手。根据章节梗概生成详细的场景正文。" +
                    "要求：1. 字数约3000字；2. 文笔流畅，情节生动；3. 严格遵守人物设定、世界规则和剧情线程；4. 参考相似风格但要有创新。";
            
            String template = "章节标题：{chapterTitle}\n" +
                    "章节梗概：{outline}\n" +
                    "关键人物：{keyCharacters}\n" +
                    "关键事件：{keyEvents}\n" +
                    "{worldContext}\n" +
                    "{characterContext}\n" +
                    "{plotContext}\n" +
                    "{unresolvedContext}\n" +
                    "{ragContext}\n\n" +
                    "请根据以上信息生成详细的场景正文（约3000字），要求文笔流畅，情节生动，符合章节梗概，并确保前后设定一致。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("chapterTitle", outline.getChapterTitle());
            variables.put("outline", outline.getOutline());
            variables.put("keyCharacters", outline.getKeyCharacters() != null ? 
                    String.join("、", outline.getKeyCharacters()) : "");
            variables.put("keyEvents", outline.getKeyEvents() != null ? 
                    String.join("、", outline.getKeyEvents()) : "");
            variables.put("worldContext", worldContext);
            variables.put("characterContext", characterContext);
            variables.put("plotContext", plotContext);
            variables.put("unresolvedContext", unresolvedContext);
            variables.put("ragContext", ragContext.toString());
            
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
    private String extractLocation(String content) {
        // 简单提取地点关键词
        String[] locations = {"山", "洞", "城", "府", "院", "林", "海", "湖"};
        for (String loc : locations) {
            if (content.contains(loc)) {
                return loc + "（待完善）";
            }
        }
        return "待确定";
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
