package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.service.kg.ExtractedEntitySyncService;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KgCharacterSyncUtil;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;
import cn.bugstack.novel.domain.service.persistence.INovelGenerationStoreService;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.util.ArrayList;
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
    
    @Autowired(required = false)
    private IKnowledgeGraphService knowledgeGraphService;

    @Resource
    private ExtractedEntitySyncService extractedEntitySyncService;
    
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
            if (chapterId != null && outline != null) {
                outline.setChapterId(chapterId);
            }
            
            // 将场景中出现的人物同步到知识图谱，便于关系与一致性校验
            if (knowledgeGraphService != null && scene != null && scene.getCharacters() != null && scene.getCharacters().length > 0) {
                String novelId = context.getNovelId() != null ? context.getNovelId() : "";
                List<Character> toSync = KgCharacterSyncUtil.buildCharactersFromScene(novelId, scene.getCharacters(), "配角");
                for (Character c : toSync) {
                    try {
                        knowledgeGraphService.createCharacter(c);
                    } catch (Exception e) {
                        log.warn("场景人物写入知识图谱失败，跳过，name={}, err: {}", c.getName(), e.getMessage());
                    }
                }
                if (!toSync.isEmpty()) {
                    log.info("KG存储: 场景人物已同步到知识图谱, novelId={}, count={}", novelId, toSync.size());
                }

                Map<String, Object> relationProps = new java.util.HashMap<>();
                relationProps.put("chapterId", outline != null ? outline.getChapterId() : null);
                relationProps.put("sceneId", scene.getSceneId());
                relationProps.put("sceneTitle", scene.getSceneTitle());
                relationProps.put("chapterNumber", outline != null ? outline.getChapterNumber() : null);

                String eventId = KgStorySyncUtil.toEventId(novelId, scene.getSceneTitle());
                java.util.Map<String, Object> eventProps = new java.util.HashMap<>();
                eventProps.put("chapterId", outline != null ? outline.getChapterId() : null);
                eventProps.put("chapterNumber", outline != null ? outline.getChapterNumber() : null);
                eventProps.put("sceneId", scene.getSceneId());
                eventProps.put("summary", StoryMemoryDocumentUtil.excerpt(scene.getContent(), 220));
                knowledgeGraphService.upsertEntity(novelId, "Event", eventId, scene.getSceneTitle(), eventProps);

                if (KgStorySyncUtil.hasMeaningfulText(scene.getLocation())) {
                    String locationId = KgStorySyncUtil.toLocationId(novelId, scene.getLocation());
                    java.util.Map<String, Object> locationProps = new java.util.HashMap<>();
                    locationProps.put("description", outline != null ? outline.getOutline() : "");
                    locationProps.put("latestSceneId", scene.getSceneId());
                    locationProps.put("latestChapterId", outline != null ? outline.getChapterId() : null);
                    knowledgeGraphService.upsertEntity(novelId, "Location", locationId, scene.getLocation(), locationProps);
                    knowledgeGraphService.createEntityRelationship("Event", eventId, "Location", locationId, "OCCURS_AT", relationProps);
                    for (String characterName : scene.getCharacters()) {
                        String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                        knowledgeGraphService.createEntityRelationship("Character", characterId, "Location", locationId, "APPEARS_AT", relationProps);
                    }
                }

                for (String characterName : scene.getCharacters()) {
                    String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                    knowledgeGraphService.createEntityRelationship("Character", characterId, "Event", eventId, "PARTICIPATES_IN", relationProps);
                }

                if (outline != null && outline.getForeshadowing() != null) {
                    for (String threadTitle : outline.getForeshadowing()) {
                        if (!KgStorySyncUtil.hasMeaningfulText(threadTitle)) {
                            continue;
                        }
                        String threadId = KgStorySyncUtil.toPlotThreadId(novelId, threadTitle);
                        knowledgeGraphService.upsertPlotThread(novelId, threadId, threadTitle, "ACTIVE", relationProps);
                        knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Event", eventId, "ADVANCES", relationProps);
                    }
                }
            }

            // 信息抽取 Agent：从正文深挖 人物/地点/势力/法宝/功法/关系/伏笔，补充到 KG
            if (extractedEntitySyncService != null && knowledgeGraphService != null && scene != null
                    && scene.getContent() != null && !scene.getContent().isBlank()) {
                try {
                    IAgent<Object[], ExtractedEntities> extractionAgent = orchestrator.getAgent("InfoExtractionAgent");
                    if (extractionAgent != null) {
                        ExtractedEntities entities = extractionAgent.execute(
                                new Object[]{outline, scene}, context);
                        extractedEntitySyncService.syncToKnowledgeGraph(
                                context.getNovelId(), entities, outline, scene);
                    }
                } catch (Exception e) {
                    log.warn("信息抽取或同步KG失败，不阻塞主流程: {}", e.getMessage());
                }
            }
            // 将生成的场景写入向量库，分别写入摘要记忆与全文记忆，便于多层检索。
            if (scene != null && scene.getContent() != null && !scene.getContent().isEmpty()) {
                try {
                    java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                    metadata.put("novelId", context.getNovelId());
                    metadata.put("volumeNumber", volumeNumber);
                    metadata.put("chapterId", outline != null ? outline.getChapterId() : null);
                    metadata.put("chapterNumber", outline != null ? outline.getChapterNumber() : null);
                    metadata.put("chapterTitle", outline != null ? outline.getChapterTitle() : null);
                    metadata.put("sceneId", scene.getSceneId());
                    metadata.put("sceneNumber", scene.getSceneNumber());
                    metadata.put("sceneTitle", scene.getSceneTitle());
                    metadata.put("sceneType", scene.getSceneType());
                    if (scene.getCharacters() != null && scene.getCharacters().length > 0) {
                        metadata.put("characters", String.join(",", scene.getCharacters()));
                    }
                    if (scene.getLocation() != null && !scene.getLocation().isEmpty()) {
                        metadata.put("location", scene.getLocation());
                    }

                    java.util.Map<String, Object> summaryMetadata = new java.util.HashMap<>(metadata);
                    summaryMetadata.put("memoryType", "scene_summary");
                    summaryMetadata.put("scope", "scene");
                    ragService.addDocument(StoryMemoryDocumentUtil.buildSceneSummaryDocument(outline, scene), "zh", summaryMetadata);

                    java.util.Map<String, Object> fulltextMetadata = new java.util.HashMap<>(metadata);
                    fulltextMetadata.put("memoryType", "scene_fulltext");
                    fulltextMetadata.put("scope", "scene");
                    ragService.addDocument(StoryMemoryDocumentUtil.buildSceneFullTextDocument(outline, scene), "zh", fulltextMetadata);

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
    
}
