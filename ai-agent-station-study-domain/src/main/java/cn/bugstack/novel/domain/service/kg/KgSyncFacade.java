package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.valobj.Character;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一收口知识图谱同步逻辑，避免执行节点分散写图谱。
 */
@Slf4j
@Service
public class KgSyncFacade {

    @Autowired(required = false)
    private IKnowledgeGraphService knowledgeGraphService;

    @Autowired(required = false)
    private ExtractedEntitySyncService extractedEntitySyncService;

    @Autowired(required = false)
    private EntityResolver entityResolver;

    @Autowired(required = false)
    private PlotThreadManager plotThreadManager;

    public void syncChapterOutline(String novelId, ChapterOutline outline) {
        if (knowledgeGraphService == null || outline == null || novelId == null || novelId.isBlank()) {
            return;
        }

        List<String> keyCharacters = KgStorySyncUtil.distinctNonBlank(outline.getKeyCharacters());
        syncOutlineCharacters(novelId, outline, keyCharacters);
        syncOutlineEvents(novelId, outline, keyCharacters);
        syncOutlineForeshadowing(novelId, outline, keyCharacters);
    }

    public void syncSceneStructure(String novelId, ChapterOutline outline, Scene scene) {
        if (knowledgeGraphService == null || scene == null || novelId == null || novelId.isBlank()) {
            return;
        }

        List<String> sceneCharacters = scene.getCharacters() == null
                ? List.of()
                : KgStorySyncUtil.distinctNonBlank(Arrays.asList(scene.getCharacters()));

        for (Character character : KgCharacterSyncUtil.buildCharactersFromScene(novelId, scene.getCharacters(), "配角")) {
            try {
                knowledgeGraphService.createCharacter(character);
            } catch (Exception e) {
                log.warn("场景人物写入知识图谱失败，跳过，name={}, err: {}", character.getName(), e.getMessage());
            }
        }

        Map<String, Object> relationProps = buildRelationProps(outline, scene);
        String eventId = KgStorySyncUtil.toSceneEventId(novelId,
                outline != null ? outline.getChapterId() : null,
                scene.getSceneId(),
                scene.getSceneTitle());
        Map<String, Object> eventProps = new HashMap<>(relationProps);
        eventProps.put("summary", cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil.excerpt(scene.getContent(), 220));
        eventProps.put("eventType", scene.getSceneType());
        knowledgeGraphService.upsertEntity(novelId, "Event", eventId, scene.getSceneTitle(), eventProps);

        if (KgStorySyncUtil.hasMeaningfulText(scene.getLocation())) {
            String locationId = KgStorySyncUtil.toLocationId(novelId, scene.getLocation());
            Map<String, Object> locationProps = new HashMap<>();
            locationProps.put("description", outline != null ? outline.getOutline() : "");
            locationProps.put("latestSceneId", scene.getSceneId());
            locationProps.put("latestChapterId", outline != null ? outline.getChapterId() : null);
            knowledgeGraphService.upsertEntity(novelId, "Location", locationId, scene.getLocation(), locationProps);
            knowledgeGraphService.createEntityRelationship("Event", eventId, "Location", locationId, "OCCURS_AT", relationProps);
            for (String characterName : sceneCharacters) {
                String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                knowledgeGraphService.createEntityRelationship("Character", characterId, "Location", locationId, "APPEARS_AT", relationProps);
            }
        }

        for (String characterName : sceneCharacters) {
            String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
            knowledgeGraphService.createEntityRelationship("Character", characterId, "Event", eventId, "PARTICIPATES_IN", relationProps);
        }

        if (outline != null) {
            for (String threadTitle : KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing())) {
                if (!KgStorySyncUtil.hasMeaningfulText(threadTitle)) {
                    continue;
                }
                String threadId = KgStorySyncUtil.toPlotThreadId(novelId, threadTitle);
                knowledgeGraphService.upsertPlotThread(novelId, threadId, threadTitle, "ACTIVE", relationProps);
                knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Event", eventId, "ADVANCES", relationProps);
            }
        }
    }

    public void syncExtractedEntities(String novelId, ExtractedEntities entities, ChapterOutline outline, Scene scene) {
        if (extractedEntitySyncService == null || knowledgeGraphService == null) {
            return;
        }
        ExtractedEntities normalized = entityResolver != null
                ? entityResolver.normalize(entities, outline, scene)
                : entities;
        extractedEntitySyncService.syncToKnowledgeGraph(novelId, normalized, outline, scene);
        if (plotThreadManager != null) {
            plotThreadManager.applySignals(novelId, normalized, outline, scene);
        }
    }

    private void syncOutlineCharacters(String novelId, ChapterOutline outline, List<String> keyCharacters) {
        List<Character> toSync = KgCharacterSyncUtil.buildCharactersForSync(novelId, keyCharacters, "配角");
        for (Character character : toSync) {
            try {
                knowledgeGraphService.createCharacter(character);
            } catch (Exception e) {
                log.warn("人物写入知识图谱失败，跳过，name={}, err: {}", character.getName(), e.getMessage());
            }
        }

        if (!toSync.isEmpty()) {
            log.info("KG存储: 本章关键人物已同步到知识图谱, novelId={}, count={}", novelId, toSync.size());
        }

        if (keyCharacters.size() < 2) {
            return;
        }

        for (int i = 0; i < keyCharacters.size(); i++) {
            for (int j = i + 1; j < keyCharacters.size(); j++) {
                String fromId = KgCharacterSyncUtil.toCharacterId(novelId, keyCharacters.get(i));
                String toId = KgCharacterSyncUtil.toCharacterId(novelId, keyCharacters.get(j));
                try {
                    Map<String, Object> relProps = new HashMap<>();
                    relProps.put("chapterId", outline.getChapterId());
                    relProps.put("chapterNumber", outline.getChapterNumber());
                    knowledgeGraphService.createRelationship(fromId, toId, "RELATED_TO", relProps);
                } catch (Exception e) {
                    log.warn("人物关系写入知识图谱失败，跳过，{}->{}, err: {}", fromId, toId, e.getMessage());
                }
            }
        }
    }

    private void syncOutlineEvents(String novelId, ChapterOutline outline, List<String> keyCharacters) {
        for (String eventName : KgStorySyncUtil.distinctNonBlank(outline.getKeyEvents())) {
            if (!KgStorySyncUtil.hasMeaningfulText(eventName)) {
                continue;
            }
            try {
                String eventId = KgStorySyncUtil.toChapterEventId(novelId, outline.getChapterId(), eventName);
                Map<String, Object> eventProps = new HashMap<>();
                eventProps.put("chapterId", outline.getChapterId());
                eventProps.put("chapterNumber", outline.getChapterNumber());
                eventProps.put("summary", outline.getOutline());
                knowledgeGraphService.upsertEntity(novelId, "Event", eventId, eventName, eventProps);
                for (String characterName : keyCharacters) {
                    String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                    knowledgeGraphService.createEntityRelationship("Character", characterId, "Event", eventId, "INVOLVED_IN", eventProps);
                }
            } catch (Exception e) {
                log.warn("章节事件写入知识图谱失败，event={}, err={}", eventName, e.getMessage());
            }
        }
    }

    private void syncOutlineForeshadowing(String novelId, ChapterOutline outline, List<String> keyCharacters) {
        String chapterId = outline.getChapterId() != null ? outline.getChapterId() : UUID.randomUUID().toString();
        int index = 0;
        for (String content : KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing())) {
            if (!KgStorySyncUtil.hasMeaningfulText(content)) {
                continue;
            }
            try {
                Map<String, Object> props = new HashMap<>();
                props.put("novelId", novelId);
                props.put("chapterId", chapterId);
                props.put("chapterNumber", outline.getChapterNumber());
                props.put("resolved", false);

                String foreshadowingId = "f-" + chapterId + "-" + index++;
                knowledgeGraphService.createForeshadowing(foreshadowingId, content, props);

                String threadId = KgStorySyncUtil.toPlotThreadId(novelId, content);
                Map<String, Object> threadProps = new HashMap<>(props);
                threadProps.put("summary", outline.getOutline());
                knowledgeGraphService.upsertPlotThread(novelId, threadId, content, "ACTIVE", threadProps);
                for (String characterName : keyCharacters) {
                    String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                    knowledgeGraphService.createEntityRelationship("Character", characterId, "PlotThread", threadId, "INVOLVED_IN", threadProps);
                }
                knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Foreshadowing", foreshadowingId, "REFERS_TO", threadProps);
            } catch (Exception e) {
                log.warn("章节伏笔/剧情线程写入知识图谱失败，content={}, err={}", content, e.getMessage());
            }
        }
    }

    private Map<String, Object> buildRelationProps(ChapterOutline outline, Scene scene) {
        Map<String, Object> relationProps = new HashMap<>();
        relationProps.put("chapterId", outline != null ? outline.getChapterId() : null);
        relationProps.put("sceneId", scene != null ? scene.getSceneId() : null);
        relationProps.put("sceneTitle", scene != null ? scene.getSceneTitle() : null);
        relationProps.put("chapterNumber", outline != null ? outline.getChapterNumber() : null);
        return relationProps;
    }
}
