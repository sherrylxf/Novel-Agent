package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.valobj.Character;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        // 1. 新人物 -> Character（结合 outline.keyCharacters 与关系密度推断主角/重要配角）
        String protagonistName = outline != null && outline.getKeyCharacters() != null && !outline.getKeyCharacters().isEmpty()
                ? extractNameFromKeyCharacter(outline.getKeyCharacters().get(0)) : null;
        Set<String> outlineCharacters = normalizeNames(outline != null ? outline.getKeyCharacters() : null);
        Map<String, Integer> relationWeight = buildRelationWeight(entities);
        for (String name : KgStorySyncUtil.distinctNonBlank(entities.getCharacters())) {
            try {
                String normalized = name != null ? name.trim() : "";
                String type = inferCharacterType(normalized, protagonistName, outlineCharacters, relationWeight);
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

        // 6. 事件 -> Event（升级为原子剧情节点，并补参与者/地点/势力/剧情线）
        syncEvents(novelId, entities, chapterId, chapterNumber, sceneId, relationProps);

        // 7. 关系 -> createRelationship / createEntityRelationship
        for (ExtractedEntities.RelationTriple triple : entities.getRelations()) {
            if (triple == null) continue;
            try {
                String fromId = KgCharacterSyncUtil.toCharacterId(novelId, triple.getSubject());
                RelationTarget target = resolveRelationTarget(novelId, entities, triple.getObject());
                String relType = sanitizeRelationType(triple.getRelationType());
                knowledgeGraphService.createEntityRelationship("Character", fromId, target.type(), target.id(), relType, relationProps);
                if ("Character".equals(target.type())) {
                    knowledgeGraphService.createRelationship(fromId, target.id(), relType, relationProps);
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

        // 9. 状态变化 -> 更新实体状态字段
        applyStateChanges(novelId, entities, chapterId, chapterNumber, sceneId);

        log.info("抽取实体已同步到KG: novelId={}, 人物+地点+势力+法宝+功法+事件+关系+伏笔+状态变化", novelId);
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

    private static String inferCharacterType(String normalizedName, String protagonistName, Set<String> outlineCharacters,
                                             Map<String, Integer> relationWeight) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return "配角";
        }
        if (protagonistName != null && protagonistName.equals(normalizedName)) {
            return "主角";
        }
        if (outlineCharacters.contains(normalizedName)) {
            return "重要配角";
        }
        int weight = relationWeight.getOrDefault(normalizedName, 0);
        return weight >= 2 ? "重要配角" : "配角";
    }

    private static Set<String> normalizeNames(List<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String item : KgStorySyncUtil.distinctNonBlank(names)) {
            normalized.add(extractNameFromKeyCharacter(item));
        }
        return normalized;
    }

    private static Map<String, Integer> buildRelationWeight(ExtractedEntities entities) {
        Map<String, Integer> weights = new HashMap<>();
        if (entities == null || entities.getRelations() == null) {
            return weights;
        }
        for (ExtractedEntities.RelationTriple triple : entities.getRelations()) {
            if (triple == null) {
                continue;
            }
            increaseWeight(weights, triple.getSubject(), 2);
            increaseWeight(weights, triple.getObject(), 1);
        }
        return weights;
    }

    private static void increaseWeight(Map<String, Integer> weights, String name, int delta) {
        if (name == null || name.isBlank()) {
            return;
        }
        String normalized = name.trim();
        weights.put(normalized, weights.getOrDefault(normalized, 0) + delta);
    }

    private static RelationTarget resolveRelationTarget(String novelId, ExtractedEntities entities, String objectName) {
        String normalized = objectName != null ? objectName.trim() : "";
        if (normalized.isEmpty()) {
            return new RelationTarget("Character", KgCharacterSyncUtil.toCharacterId(novelId, objectName));
        }
        if (containsEntity(entities != null ? entities.getFactions() : null, normalized)) {
            return new RelationTarget("Faction", KgStorySyncUtil.toFactionId(novelId, normalized));
        }
        if (containsEntity(entities != null ? entities.getArtifacts() : null, normalized)) {
            return new RelationTarget("Artifact", KgStorySyncUtil.toArtifactId(novelId, normalized));
        }
        if (containsEntity(entities != null ? entities.getTechniques() : null, normalized)) {
            return new RelationTarget("Technique", KgStorySyncUtil.toTechniqueId(novelId, normalized));
        }
        if (containsEntity(entities != null ? entities.getLocations() : null, normalized)) {
            return new RelationTarget("Location", KgStorySyncUtil.toLocationId(novelId, normalized));
        }
        if (containsEvent(entities != null ? entities.getEvents() : null, normalized)) {
            return new RelationTarget("Event", KgStorySyncUtil.toEventId(novelId, normalized));
        }
        return new RelationTarget("Character", KgCharacterSyncUtil.toCharacterId(novelId, normalized));
    }

    private static boolean containsEntity(List<String> entities, String target) {
        if (entities == null || target == null || target.isBlank()) {
            return false;
        }
        for (String item : entities) {
            if (item != null && target.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEvent(List<ExtractedEntities.EventRecord> events, String target) {
        if (events == null || target == null || target.isBlank()) {
            return false;
        }
        for (ExtractedEntities.EventRecord event : events) {
            if (event != null && event.getName() != null && target.equals(event.getName().trim())) {
                return true;
            }
        }
        return false;
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

    private void syncEvents(String novelId, ExtractedEntities entities, String chapterId, Integer chapterNumber,
                            String sceneId, Map<String, Object> relationProps) {
        if (entities.getEvents() == null) {
            return;
        }
        for (ExtractedEntities.EventRecord event : entities.getEvents()) {
            if (event == null || !KgStorySyncUtil.hasMeaningfulText(event.getName())) {
                continue;
            }
            try {
                String eventId = KgStorySyncUtil.toSceneEventId(novelId, chapterId, sceneId, event.getName());
                Map<String, Object> props = new HashMap<>();
                props.put("chapterId", chapterId);
                props.put("sceneId", sceneId);
                props.put("chapterNumber", chapterNumber);
                props.put("summary", valueOrDefault(event.getSummary(), event.getName()));
                props.put("eventType", valueOrDefault(event.getEventType(), "GENERAL"));
                props.put("outcome", valueOrDefault(event.getOutcome(), ""));
                if (event.getImportance() != null) {
                    props.put("importance", event.getImportance());
                }
                knowledgeGraphService.upsertEntity(novelId, "Event", eventId, event.getName(), props);

                if (KgStorySyncUtil.hasMeaningfulText(event.getLocation())) {
                    String locationId = KgStorySyncUtil.toLocationId(novelId, event.getLocation());
                    knowledgeGraphService.upsertEntity(novelId, "Location", locationId, event.getLocation(),
                            Map.of("chapterId", chapterId, "sceneId", sceneId));
                    knowledgeGraphService.createEntityRelationship("Event", eventId, "Location", locationId, "HAPPENS_IN", relationProps);
                }

                for (String factionName : KgStorySyncUtil.distinctNonBlank(event.getFactions())) {
                    String factionId = KgStorySyncUtil.toFactionId(novelId, factionName);
                    knowledgeGraphService.upsertEntity(novelId, "Faction", factionId, factionName,
                            Map.of("chapterId", chapterId, "sceneId", sceneId));
                    knowledgeGraphService.createEntityRelationship("Faction", factionId, "Event", eventId, "INVOLVED_IN", relationProps);
                }

                if (event.getParticipants() != null) {
                    for (ExtractedEntities.EventParticipant participant : event.getParticipants()) {
                        syncEventParticipant(novelId, eventId, participant, relationProps);
                    }
                }

                for (String threadTitle : KgStorySyncUtil.distinctNonBlank(event.getRelatedThreads())) {
                    String threadId = KgStorySyncUtil.toPlotThreadId(novelId, threadTitle);
                    Map<String, Object> threadProps = new HashMap<>(relationProps);
                    threadProps.put("summary", valueOrDefault(event.getSummary(), threadTitle));
                    knowledgeGraphService.upsertPlotThread(novelId, threadId, threadTitle, "ACTIVE", threadProps);
                    knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Event", eventId, "ADVANCED_BY", relationProps);
                }
            } catch (Exception e) {
                log.warn("同步事件失败 event={}: {}", event.getName(), e.getMessage());
            }
        }
    }

    private void syncEventParticipant(String novelId, String eventId, ExtractedEntities.EventParticipant participant,
                                      Map<String, Object> relationProps) {
        if (participant == null || !KgStorySyncUtil.hasMeaningfulText(participant.getName())) {
            return;
        }
        String entityType = participant.getEntityType() != null && !participant.getEntityType().isBlank()
                ? participant.getEntityType().trim() : "Character";
        String relationType = "PARTICIPATES_IN";
        String targetId;
        if ("Faction".equalsIgnoreCase(entityType)) {
            targetId = KgStorySyncUtil.toFactionId(novelId, participant.getName());
            knowledgeGraphService.upsertEntity(novelId, "Faction", targetId, participant.getName(), new HashMap<>());
            relationType = "INVOLVED_IN";
            entityType = "Faction";
        } else {
            targetId = KgCharacterSyncUtil.toCharacterId(novelId, participant.getName());
            Character character = Character.builder()
                    .characterId(targetId)
                    .name(participant.getName())
                    .novelId(novelId)
                    .type("配角")
                    .personality("")
                    .background("")
                    .abilities("")
                    .build();
            knowledgeGraphService.createCharacter(character);
            entityType = "Character";
        }
        Map<String, Object> participantProps = new HashMap<>(relationProps);
        participantProps.put("role", valueOrDefault(participant.getRole(), ""));
        participantProps.put("outcome", valueOrDefault(participant.getOutcome(), ""));
        knowledgeGraphService.createEntityRelationship(entityType, targetId, "Event", eventId, relationType, participantProps);
    }

    private void applyStateChanges(String novelId, ExtractedEntities entities, String chapterId,
                                   Integer chapterNumber, String sceneId) {
        if (entities.getStateChanges() == null) {
            return;
        }
        for (ExtractedEntities.StateChange change : entities.getStateChanges()) {
            if (change == null || change.getEntityType() == null || change.getName() == null || change.getField() == null) {
                continue;
            }
            try {
                String entityType = change.getEntityType().trim();
                String name = change.getName().trim();
                String field = change.getField().trim();
                String newValue = valueOrDefault(change.getNewValue(), "");
                if ("Character".equalsIgnoreCase(entityType)) {
                    Character current = knowledgeGraphService.queryCharacter(KgCharacterSyncUtil.toCharacterId(novelId, name));
                    Character.CharacterBuilder builder = Character.builder()
                            .characterId(current != null ? current.getCharacterId() : KgCharacterSyncUtil.toCharacterId(novelId, name))
                            .name(current != null ? current.getName() : name)
                            .novelId(current != null ? current.getNovelId() : novelId)
                            .type(current != null ? current.getType() : "配角")
                            .personality(current != null ? current.getPersonality() : "")
                            .background(current != null ? current.getBackground() : "")
                            .abilities(current != null ? current.getAbilities() : "");
                    if ("status".equalsIgnoreCase(field) || "goal".equalsIgnoreCase(field)) {
                        Map<String, Object> attrs = current != null && current.getAttributes() != null
                                ? new HashMap<>(current.getAttributes()) : new HashMap<>();
                        attrs.put(field, newValue);
                        attrs.put("chapterId", chapterId);
                        attrs.put("chapterNumber", chapterNumber);
                        attrs.put("sceneId", sceneId);
                        attrs.put("changeReason", valueOrDefault(change.getReason(), ""));
                        builder.attributes(attrs);
                    }
                    knowledgeGraphService.updateCharacter(builder.build());
                } else {
                    String entityId = resolveEntityIdByType(novelId, entityType, name);
                    Map<String, Object> props = new HashMap<>();
                    props.put(field, newValue);
                    props.put("chapterId", chapterId);
                    props.put("chapterNumber", chapterNumber);
                    props.put("sceneId", sceneId);
                    props.put("changeReason", valueOrDefault(change.getReason(), ""));
                    knowledgeGraphService.upsertEntity(novelId, entityType, entityId, name, props);
                }
            } catch (Exception e) {
                log.warn("同步状态变化失败 {}:{} -> {}", change.getEntityType(), change.getName(), e.getMessage());
            }
        }
    }

    private static String resolveEntityIdByType(String novelId, String entityType, String name) {
        if ("Faction".equalsIgnoreCase(entityType)) return KgStorySyncUtil.toFactionId(novelId, name);
        if ("Location".equalsIgnoreCase(entityType)) return KgStorySyncUtil.toLocationId(novelId, name);
        if ("Artifact".equalsIgnoreCase(entityType)) return KgStorySyncUtil.toArtifactId(novelId, name);
        if ("Technique".equalsIgnoreCase(entityType)) return KgStorySyncUtil.toTechniqueId(novelId, name);
        if ("Event".equalsIgnoreCase(entityType)) return KgStorySyncUtil.toEventId(novelId, name);
        return KgStorySyncUtil.toEntityId(novelId, "entity", name);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private record RelationTarget(String type, String id) {}
}
