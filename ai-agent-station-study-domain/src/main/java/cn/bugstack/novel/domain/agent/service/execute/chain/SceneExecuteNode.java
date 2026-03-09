package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.service.kg.KgSyncFacade;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;
import cn.bugstack.novel.domain.service.persistence.INovelGenerationStoreService;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 场景执行节点
 * 生成场景正文
 */
@Slf4j
@Component
public class SceneExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private INovelGenerationStoreService generationStoreService;
    
    @Resource
    private IRAGService ragService;
    
    @Resource
    private KgSyncFacade kgSyncFacade;
    
    @Resource
    private ValidationExecuteNode validationExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("场景节点：开始生成场景正文");
        
        // 从上下文获取ChapterOutline
        ChapterOutline outline = context.getAttribute("currentChapter");
        
        // 执行场景生成
        IAgent<ChapterOutline, Scene> sceneAgent = orchestrator.getAgent("SceneGenerationAgent");
        Scene scene = sceneAgent.execute(outline, context);
        
        // 保存到上下文
        context.setAttribute("currentScene", scene);
        
        // 同步到 outline.scenes，便于后续保存/展示
        if (outline != null) {
            if (outline.getScenes() == null) {
                outline.setScenes(new ArrayList<>());
            }
            outline.getScenes().removeIf(s -> s != null && s.getSceneNumber() != null
                    && scene != null && s.getSceneNumber().equals(scene.getSceneNumber()));
            outline.getScenes().add(scene);
        }
        
        // 自动落库 + 导出txt（不阻塞主流程）
        try {
            VolumePlan volumePlan = context.getAttribute("currentVolume");
            Integer volumeNumber = volumePlan != null ? volumePlan.getVolumeNumber() : null;
            String chapterId = generationStoreService.persistChapterAndScene(context.getNovelId(), volumeNumber, outline, scene);
            ExtractedEntities extractedEntities = null;
            if (chapterId != null && outline != null) {
                outline.setChapterId(chapterId);
            }
            
            if (scene != null) {
                kgSyncFacade.syncSceneStructure(context.getNovelId(), outline, scene);
            }

            // 信息抽取 Agent：从正文深挖 人物/地点/势力/法宝/功法/关系/伏笔，补充到 KG
            if (scene != null && scene.getContent() != null && !scene.getContent().isBlank()) {
                try {
                    IAgent<Object[], ExtractedEntities> extractionAgent = orchestrator.getAgent("InfoExtractionAgent");
                    if (extractionAgent != null) {
                        extractedEntities = extractionAgent.execute(
                                new Object[]{outline, scene}, context);
                        kgSyncFacade.syncExtractedEntities(context.getNovelId(), extractedEntities, outline, scene);
                    }
                } catch (Exception e) {
                    log.warn("信息抽取或同步KG失败，不阻塞主流程: {}", e.getMessage());
                }
            }
            // 将生成的场景写入向量库，分别写入摘要记忆与全文记忆，便于多层检索。
            if (scene != null && scene.getContent() != null && !scene.getContent().isEmpty()) {
                try {
                    java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                    String createdAt = LocalDateTime.now().toString();
                    List<String> eventNames = collectEventNames(outline, scene, extractedEntities);
                    List<String> plotThreadTitles = collectPlotThreadTitles(outline, extractedEntities);
                    List<String> characterNames = collectCharacterNames(scene, extractedEntities);
                    metadata.put("novelId", context.getNovelId());
                    metadata.put("volumeNumber", volumeNumber);
                    metadata.put("chapterId", outline != null ? outline.getChapterId() : null);
                    metadata.put("chapterNumber", outline != null ? outline.getChapterNumber() : null);
                    metadata.put("chapterTitle", outline != null ? outline.getChapterTitle() : null);
                    metadata.put("sceneId", scene.getSceneId());
                    metadata.put("sceneNumber", scene.getSceneNumber());
                    metadata.put("sceneTitle", scene.getSceneTitle());
                    metadata.put("sceneType", scene.getSceneType());
                    if (!characterNames.isEmpty()) {
                        metadata.put("characters", String.join(",", characterNames));
                    }
                    if (scene.getLocation() != null && !scene.getLocation().isEmpty()) {
                        metadata.put("location", scene.getLocation());
                    }
                    metadata.put("characterIds", KgStorySyncUtil.toCharacterIds(context.getNovelId(),
                            characterNames));
                    metadata.put("eventIds", buildSceneEventIds(context.getNovelId(), outline, scene, extractedEntities));
                    metadata.put("plotThreadIds", buildPlotThreadIds(context.getNovelId(), outline, extractedEntities));
                    metadata.put("events", StoryMemoryDocumentUtil.join(eventNames, ","));
                    metadata.put("plotThreads", StoryMemoryDocumentUtil.join(plotThreadTitles, ","));
                    metadata.put("factionIds", extractedEntities != null
                            ? KgStorySyncUtil.toFactionIds(context.getNovelId(), extractedEntities.getFactions())
                            : new ArrayList<String>());
                    metadata.put("createdAt", createdAt);
                    metadata.put("importance", resolveImportance(extractedEntities));

                    java.util.Map<String, Object> summaryMetadata = new java.util.HashMap<>(metadata);
                    summaryMetadata.put("memoryType", "scene_summary");
                    summaryMetadata.put("scope", "scene");
                    ragService.addDocument(StoryMemoryDocumentUtil.buildSceneSummaryDocument(outline, scene), "zh", summaryMetadata);

                    java.util.Map<String, Object> fulltextMetadata = new java.util.HashMap<>(metadata);
                    fulltextMetadata.put("memoryType", "scene_fulltext");
                    fulltextMetadata.put("scope", "scene");
                    ragService.addDocument(StoryMemoryDocumentUtil.buildSceneFullTextDocument(outline, scene), "zh", fulltextMetadata);

                    for (String characterName : characterNames) {
                        java.util.Map<String, Object> characterMetadata = new java.util.HashMap<>(metadata);
                        characterMetadata.put("memoryType", "character_memory");
                        characterMetadata.put("scope", "character");
                        characterMetadata.put("characters", characterName);
                        characterMetadata.put("importance", Math.max(resolveImportance(extractedEntities), 0.7D));
                        ragService.addDocument(
                                StoryMemoryDocumentUtil.buildCharacterMemoryDocument(
                                        characterName,
                                        outline,
                                        scene,
                                        eventNames,
                                        plotThreadTitles),
                                "zh",
                                characterMetadata);
                    }

                    for (ExtractedEntities.PlotThreadSignal signal : extractedEntities != null
                            ? extractedEntities.getPlotThreadSignals()
                            : List.<ExtractedEntities.PlotThreadSignal>of()) {
                        if (signal == null || !KgStorySyncUtil.hasMeaningfulText(signal.getThreadTitle())) {
                            continue;
                        }
                        java.util.Map<String, Object> threadMetadata = new java.util.HashMap<>(metadata);
                        threadMetadata.put("memoryType", "plot_thread_summary");
                        threadMetadata.put("scope", "plot_thread");
                        threadMetadata.put("plotThreads", signal.getThreadTitle().trim());
                        threadMetadata.put("importance", Math.max(resolveImportance(extractedEntities), 0.75D));
                        ragService.addDocument(
                                StoryMemoryDocumentUtil.buildPlotThreadSummaryDocument(
                                        signal.getThreadTitle().trim(),
                                        outline,
                                        scene,
                                        signal.getSummary() != null ? signal.getSummary() : signal.getEvidence(),
                                        eventNames),
                                "zh",
                                threadMetadata);
                    }

                    log.info("RAG存储: 场景摘要与正文已写入向量库，novelId={}, sceneId={}, chapterNumber={}", context.getNovelId(), scene.getSceneId(), outline != null ? outline.getChapterNumber() : null);
                } catch (Exception e) {
                    log.warn("场景写入向量库失败，但不影响主流程，novelId: {}, err: {}", context.getNovelId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("章节/场景落库或导出失败，但继续执行，novelId: {}, err: {}", context.getNovelId(), e.getMessage());
        }
        context.setCurrentStage(GenerationStage.VALIDATION.name());
        
        return validationExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return validationExecuteNode;
    }

    private List<String> buildSceneEventIds(String novelId, ChapterOutline outline, Scene scene, ExtractedEntities extractedEntities) {
        if (extractedEntities != null && extractedEntities.getEvents() != null && !extractedEntities.getEvents().isEmpty()) {
            return KgStorySyncUtil.toEventIds(novelId,
                    outline != null ? outline.getChapterId() : null,
                    scene != null ? scene.getSceneId() : null,
                    extractedEntities.getEvents());
        }
        if (scene == null || !KgStorySyncUtil.hasMeaningfulText(scene.getSceneTitle())) {
            return new ArrayList<>();
        }
        return List.of(KgStorySyncUtil.toSceneEventId(novelId,
                outline != null ? outline.getChapterId() : null,
                scene.getSceneId(),
                scene.getSceneTitle()));
    }

    private List<String> buildPlotThreadIds(String novelId, ChapterOutline outline, ExtractedEntities extractedEntities) {
        List<String> threadTitles = new ArrayList<>();
        if (outline != null && outline.getForeshadowing() != null) {
            threadTitles.addAll(KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing()));
        }
        if (extractedEntities != null && extractedEntities.getPlotThreadSignals() != null) {
            for (ExtractedEntities.PlotThreadSignal signal : extractedEntities.getPlotThreadSignals()) {
                if (signal != null && KgStorySyncUtil.hasMeaningfulText(signal.getThreadTitle())) {
                    threadTitles.add(signal.getThreadTitle().trim());
                }
            }
        }
        return KgStorySyncUtil.toPlotThreadIds(novelId, threadTitles);
    }

    private List<String> collectEventNames(ChapterOutline outline, Scene scene, ExtractedEntities extractedEntities) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (outline != null && outline.getKeyEvents() != null) {
            names.addAll(KgStorySyncUtil.distinctNonBlank(outline.getKeyEvents()));
        }
        if (extractedEntities != null && extractedEntities.getEvents() != null) {
            for (ExtractedEntities.EventRecord event : extractedEntities.getEvents()) {
                if (event != null && KgStorySyncUtil.hasMeaningfulText(event.getName())) {
                    names.add(event.getName().trim());
                }
            }
        }
        if (scene != null && KgStorySyncUtil.hasMeaningfulText(scene.getSceneTitle())) {
            names.add(scene.getSceneTitle().trim());
        }
        return new ArrayList<>(names);
    }

    private List<String> collectPlotThreadTitles(ChapterOutline outline, ExtractedEntities extractedEntities) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        if (outline != null && outline.getForeshadowing() != null) {
            titles.addAll(KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing()));
        }
        if (extractedEntities != null && extractedEntities.getPlotThreadSignals() != null) {
            for (ExtractedEntities.PlotThreadSignal signal : extractedEntities.getPlotThreadSignals()) {
                if (signal != null && KgStorySyncUtil.hasMeaningfulText(signal.getThreadTitle())) {
                    titles.add(signal.getThreadTitle().trim());
                }
            }
        }
        return new ArrayList<>(titles);
    }

    private List<String> collectCharacterNames(Scene scene, ExtractedEntities extractedEntities) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (scene != null && scene.getCharacters() != null) {
            names.addAll(KgStorySyncUtil.distinctNonBlank(scene.getCharacters()));
        }
        if (extractedEntities != null && extractedEntities.getCharacters() != null) {
            names.addAll(KgStorySyncUtil.distinctNonBlank(extractedEntities.getCharacters()));
        }
        return new ArrayList<>(names);
    }

    private double resolveImportance(ExtractedEntities extractedEntities) {
        if (extractedEntities == null || extractedEntities.getEvents() == null || extractedEntities.getEvents().isEmpty()) {
            return 0.7D;
        }
        double max = 0.0D;
        for (ExtractedEntities.EventRecord event : extractedEntities.getEvents()) {
            if (event != null && event.getImportance() != null) {
                max = Math.max(max, event.getImportance());
            }
        }
        return max > 0.0D ? Math.min(max, 1.0D) : 0.7D;
    }
    
}
