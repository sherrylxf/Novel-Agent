package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.valobj.Character;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将抽取实体同步到知识图谱
 * 支持 Faction / Artifact / Technique 及人物关系
 */
@Slf4j
@Service
public class ExtractedEntitySyncService {

    @Resource
    private IKnowledgeGraphService knowledgeGraphService;

    /**
     * 将 InfoExtractionAgent 输出同步到 Neo4j
     */
    public void syncToKnowledgeGraph(String novelId, ExtractedEntities entities,
                                     ChapterOutline outline, Scene scene) {
        if (knowledgeGraphService == null || entities == null || novelId == null) return;
        novelId = novelId.trim();
        if (novelId.isEmpty()) return;

        String chapterId = outline != null ? outline.getChapterId() : null;
        Integer chapterNumber = outline != null ? outline.getChapterNumber() : null;
        String sceneId = scene != null ? scene.getSceneId() : null;

        Map<String, Object> relationProps = new HashMap<>();
        relationProps.put("chapterId", chapterId);
        relationProps.put("chapterNumber", chapterNumber);
        relationProps.put("sceneId", sceneId);

        // 1. 新人物 -> Character（结合 outline.keyCharacters 推断主角，支持「主角'林衍'」格式）
        String protagonistName = outline != null && outline.getKeyCharacters() != null && !outline.getKeyCharacters().isEmpty()
                ? extractNameFromKeyCharacter(outline.getKeyCharacters().get(0)) : null;
        for (String name : KgStorySyncUtil.distinctNonBlank(entities.getCharacters())) {
            try {
                String normalized = name != null ? name.trim() : "";
                String type = (protagonistName != null && protagonistName.equals(normalized)) ? "主角" : "配角";
                Character c = Character.builder()
                        .characterId(KgCharacterSyncUtil.toCharacterId(novelId, name))
                        .name(name)
                        .novelId(novelId)
                        .type(type)
                        .personality("")
                        .background("")
                        .abilities("")
                        .build();
                knowledgeGraphService.createCharacter(c);
            } catch (Exception e) {
                log.warn("同步人物失败 name={}: {}", name, e.getMessage());
            }
        }

        // 2. 地点 -> Location
        for (String loc : KgStorySyncUtil.distinctNonBlank(entities.getLocations())) {
            if (!KgStorySyncUtil.hasMeaningfulText(loc)) continue;
            try {
                String id = KgStorySyncUtil.toLocationId(novelId, loc);
                Map<String, Object> props = new HashMap<>();
                props.put("chapterId", chapterId);
                props.put("sceneId", sceneId);
                knowledgeGraphService.upsertEntity(novelId, "Location", id, loc, props);
            } catch (Exception e) {
                log.warn("同步地点失败 loc={}: {}", loc, e.getMessage());
            }
        }

        // 3. 势力 -> Faction
        for (String fac : KgStorySyncUtil.distinctNonBlank(entities.getFactions())) {
            if (!KgStorySyncUtil.hasMeaningfulText(fac)) continue;
            try {
                String id = KgStorySyncUtil.toFactionId(novelId, fac);
                Map<String, Object> props = new HashMap<>();
                props.put("chapterId", chapterId);
                props.put("sceneId", sceneId);
                knowledgeGraphService.upsertEntity(novelId, "Faction", id, fac, props);
            } catch (Exception e) {
                log.warn("同步势力失败 fac={}: {}", fac, e.getMessage());
            }
        }

        // 4. 法宝 -> Artifact
        for (String art : KgStorySyncUtil.distinctNonBlank(entities.getArtifacts())) {
            if (!KgStorySyncUtil.hasMeaningfulText(art)) continue;
            try {
                String id = KgStorySyncUtil.toArtifactId(novelId, art);
                Map<String, Object> props = new HashMap<>();
                props.put("chapterId", chapterId);
                props.put("sceneId", sceneId);
                knowledgeGraphService.upsertEntity(novelId, "Artifact", id, art, props);
            } catch (Exception e) {
                log.warn("同步法宝失败 art={}: {}", art, e.getMessage());
            }
        }

        // 5. 功法 -> Technique
        for (String tec : KgStorySyncUtil.distinctNonBlank(entities.getTechniques())) {
            if (!KgStorySyncUtil.hasMeaningfulText(tec)) continue;
            try {
                String id = KgStorySyncUtil.toTechniqueId(novelId, tec);
                Map<String, Object> props = new HashMap<>();
                props.put("chapterId", chapterId);
                props.put("sceneId", sceneId);
                knowledgeGraphService.upsertEntity(novelId, "Technique", id, tec, props);
            } catch (Exception e) {
                log.warn("同步功法失败 tec={}: {}", tec, e.getMessage());
            }
        }

        // 6. 事件 -> Event
        for (String evt : KgStorySyncUtil.distinctNonBlank(entities.getEvents())) {
            if (!KgStorySyncUtil.hasMeaningfulText(evt)) continue;
            try {
                String id = KgStorySyncUtil.toEventId(novelId, evt);
                Map<String, Object> props = new HashMap<>();
                props.put("chapterId", chapterId);
                props.put("sceneId", sceneId);
                props.put("summary", evt);
                props.put("chapterNumber", chapterNumber);
                knowledgeGraphService.upsertEntity(novelId, "Event", id, evt, props);
            } catch (Exception e) {
                log.warn("同步事件失败 evt={}: {}", evt, e.getMessage());
            }
        }

        // 7. 关系 -> createRelationship / createEntityRelationship
        for (ExtractedEntities.RelationTriple triple : entities.getRelations()) {
            if (triple == null) continue;
            try {
                String fromId = KgCharacterSyncUtil.toCharacterId(novelId, triple.getSubject());
                String toId;
                String toType = "Character";
                // 判断客体是人物还是势力
                if (entities.getFactions() != null && entities.getFactions().contains(triple.getObject())) {
                    toId = KgStorySyncUtil.toFactionId(novelId, triple.getObject());
                    toType = "Faction";
                } else {
                    toId = KgCharacterSyncUtil.toCharacterId(novelId, triple.getObject());
                }
                String relType = sanitizeRelationType(triple.getRelationType());
                knowledgeGraphService.createEntityRelationship("Character", fromId, toType, toId, relType, relationProps);
                if ("Character".equals(toType)) {
                    knowledgeGraphService.createRelationship(fromId, toId, relType, relationProps);
                }
            } catch (Exception e) {
                log.warn("同步关系失败 {}->{}->{}: {}", triple.getSubject(), triple.getRelationType(), triple.getObject(), e.getMessage());
            }
        }

        // 8. 伏笔 -> Foreshadowing / PlotThread，并建立 PlotThread->Foreshadowing 连线
        for (String foreshadow : KgStorySyncUtil.distinctNonBlank(entities.getForeshadowing())) {
            if (!KgStorySyncUtil.hasMeaningfulText(foreshadow)) continue;
            try {
                String threadId = KgStorySyncUtil.toPlotThreadId(novelId, foreshadow);
                Map<String, Object> props = new HashMap<>();
                props.put("novelId", novelId);
                props.put("chapterId", chapterId);
                props.put("resolved", false);
                knowledgeGraphService.upsertPlotThread(novelId, threadId, foreshadow, "ACTIVE", props);
                String foreshadowingId = "f-ext-" + threadId;
                knowledgeGraphService.createForeshadowing(foreshadowingId, foreshadow, props);
                knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Foreshadowing", foreshadowingId, "REFERS_TO", props);
            } catch (Exception e) {
                log.warn("同步伏笔失败: {}", e.getMessage());
            }
        }

        log.info("抽取实体已同步到KG: novelId={}, 人物+地点+势力+法宝+功法+事件+关系+伏笔", novelId);
    }

    private static String extractNameFromKeyCharacter(String keyChar) {
        if (keyChar == null || keyChar.isBlank()) return null;
        String s = keyChar.trim();
        if (s.startsWith("主角'") && s.contains("'")) {
            int start = s.indexOf("'") + 1;
            int end = s.indexOf("'", start);
            if (end > start) return s.substring(start, end).trim();
        }
        return s;
    }

    private static String sanitizeRelationType(String raw) {
        if (raw == null || raw.isBlank()) return "RELATED_TO";
        // Neo4j 关系类型仅支持 ASCII 标识符，将中文映射为英文
        String s = raw.trim();
        if (s.contains("师徒") || s.contains("师傅")) return "MENTOR_OF";
        if (s.contains("仇敌") || s.contains("敌人")) return "ENEMY_OF";
        if (s.contains("同盟") || s.contains("盟友")) return "ALLY_OF";
        if (s.contains("师徒") || s.contains("师父")) return "MENTOR_OF";
        if (s.contains("所属") || s.contains("加入")) return "MEMBER_OF";
        if (s.contains("拥有") || s.contains("持有")) return "OWNS";
        if (s.contains("修炼") || s.contains("学习")) return "LEARNED";
        String ascii = s.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_");
        return ascii.isEmpty() ? "RELATED_TO" : ascii.substring(0, Math.min(30, ascii.length()));
    }
}
