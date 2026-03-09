package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.model.entity.Scene;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 抽取结果进入 KG 前的轻量归一器。
 * 负责别名映射、去重和名字规范化，避免图谱持续膨胀。
 */
@Service
public class EntityResolver {

    public ExtractedEntities normalize(ExtractedEntities raw, ChapterOutline outline, Scene scene) {
        if (raw == null) {
            return ExtractedEntities.builder().build();
        }
        Map<String, String> aliasMap = buildAliasMap(raw.getAliases(), outline, scene);
        return ExtractedEntities.builder()
                .characters(canonicalizeStrings(raw.getCharacters(), aliasMap))
                .locations(canonicalizeStrings(raw.getLocations(), aliasMap))
                .factions(canonicalizeStrings(raw.getFactions(), aliasMap))
                .artifacts(canonicalizeStrings(raw.getArtifacts(), aliasMap))
                .techniques(canonicalizeStrings(raw.getTechniques(), aliasMap))
                .events(canonicalizeEvents(raw.getEvents(), aliasMap))
                .relations(canonicalizeRelations(raw.getRelations(), aliasMap))
                .foreshadowing(KgStorySyncUtil.distinctNonBlank(raw.getForeshadowing()))
                .stateChanges(canonicalizeStateChanges(raw.getStateChanges(), aliasMap))
                .plotThreadSignals(canonicalizePlotThreadSignals(raw.getPlotThreadSignals(), aliasMap))
                .aliases(normalizeAliases(raw.getAliases()))
                .build();
    }

    private Map<String, String> buildAliasMap(List<ExtractedEntities.AliasRecord> aliases, ChapterOutline outline, Scene scene) {
        Map<String, String> aliasMap = new LinkedHashMap<>();
        if (outline != null) {
            for (String name : KgStorySyncUtil.distinctNonBlank(outline.getKeyCharacters())) {
                aliasMap.put(name, name);
            }
        }
        if (scene != null) {
            for (String name : KgStorySyncUtil.distinctNonBlank(scene.getCharacters())) {
                aliasMap.put(name, name);
            }
        }
        if (aliases != null) {
            for (ExtractedEntities.AliasRecord alias : aliases) {
                if (alias == null) {
                    continue;
                }
                String canonical = safe(alias.getCanonical());
                String aliasName = safe(alias.getAlias());
                if (!canonical.isEmpty()) {
                    aliasMap.put(canonical, canonical);
                }
                if (!canonical.isEmpty() && !aliasName.isEmpty()) {
                    aliasMap.put(aliasName, canonical);
                }
            }
        }
        return aliasMap;
    }

    private List<String> canonicalizeStrings(List<String> values, Map<String, String> aliasMap) {
        List<String> normalized = new ArrayList<>();
        for (String value : KgStorySyncUtil.distinctNonBlank(values)) {
            normalized.add(resolveAlias(value, aliasMap));
        }
        return KgStorySyncUtil.distinctNonBlank(normalized);
    }

    private List<ExtractedEntities.EventRecord> canonicalizeEvents(List<ExtractedEntities.EventRecord> events, Map<String, String> aliasMap) {
        Map<String, ExtractedEntities.EventRecord> merged = new LinkedHashMap<>();
        if (events == null) {
            return new ArrayList<>();
        }
        for (ExtractedEntities.EventRecord event : events) {
            if (event == null || !KgStorySyncUtil.hasMeaningfulText(event.getName())) {
                continue;
            }
            String name = safe(event.getName());
            merged.put(name, ExtractedEntities.EventRecord.builder()
                    .name(name)
                    .eventType(safe(event.getEventType()))
                    .summary(safe(event.getSummary()))
                    .location(resolveAlias(event.getLocation(), aliasMap))
                    .outcome(safe(event.getOutcome()))
                    .importance(event.getImportance())
                    .participants(canonicalizeParticipants(event.getParticipants(), aliasMap))
                    .factions(canonicalizeStrings(event.getFactions(), aliasMap))
                    .relatedThreads(KgStorySyncUtil.distinctNonBlank(event.getRelatedThreads()))
                    .build());
        }
        return new ArrayList<>(merged.values());
    }

    private List<ExtractedEntities.EventParticipant> canonicalizeParticipants(List<ExtractedEntities.EventParticipant> participants,
                                                                              Map<String, String> aliasMap) {
        Map<String, ExtractedEntities.EventParticipant> merged = new LinkedHashMap<>();
        if (participants == null) {
            return new ArrayList<>();
        }
        for (ExtractedEntities.EventParticipant participant : participants) {
            if (participant == null || !KgStorySyncUtil.hasMeaningfulText(participant.getName())) {
                continue;
            }
            String name = resolveAlias(participant.getName(), aliasMap);
            String entityType = safe(participant.getEntityType());
            if (entityType.isEmpty()) {
                entityType = "Character";
            }
            merged.put(entityType + ":" + name, ExtractedEntities.EventParticipant.builder()
                    .name(name)
                    .entityType(entityType)
                    .role(safe(participant.getRole()))
                    .outcome(safe(participant.getOutcome()))
                    .build());
        }
        return new ArrayList<>(merged.values());
    }

    private List<ExtractedEntities.RelationTriple> canonicalizeRelations(List<ExtractedEntities.RelationTriple> relations,
                                                                         Map<String, String> aliasMap) {
        Map<String, ExtractedEntities.RelationTriple> merged = new LinkedHashMap<>();
        if (relations == null) {
            return new ArrayList<>();
        }
        for (ExtractedEntities.RelationTriple relation : relations) {
            if (relation == null) {
                continue;
            }
            String subject = resolveAlias(relation.getSubject(), aliasMap);
            String object = resolveAlias(relation.getObject(), aliasMap);
            String relationType = safe(relation.getRelationType());
            if (subject.isEmpty() || object.isEmpty() || relationType.isEmpty()) {
                continue;
            }
            merged.put(subject + "|" + relationType + "|" + object,
                    ExtractedEntities.RelationTriple.builder()
                            .subject(subject)
                            .relationType(relationType)
                            .object(object)
                            .build());
        }
        return new ArrayList<>(merged.values());
    }

    private List<ExtractedEntities.StateChange> canonicalizeStateChanges(List<ExtractedEntities.StateChange> changes,
                                                                         Map<String, String> aliasMap) {
        Map<String, ExtractedEntities.StateChange> merged = new LinkedHashMap<>();
        if (changes == null) {
            return new ArrayList<>();
        }
        for (ExtractedEntities.StateChange change : changes) {
            if (change == null) {
                continue;
            }
            String entityType = safe(change.getEntityType());
            String name = resolveAlias(change.getName(), aliasMap);
            String field = safe(change.getField());
            if (entityType.isEmpty() || name.isEmpty() || field.isEmpty()) {
                continue;
            }
            merged.put(entityType + "|" + name + "|" + field,
                    ExtractedEntities.StateChange.builder()
                            .entityType(entityType)
                            .name(name)
                            .field(field)
                            .oldValue(safe(change.getOldValue()))
                            .newValue(safe(change.getNewValue()))
                            .reason(safe(change.getReason()))
                            .build());
        }
        return new ArrayList<>(merged.values());
    }

    private List<ExtractedEntities.PlotThreadSignal> canonicalizePlotThreadSignals(List<ExtractedEntities.PlotThreadSignal> signals,
                                                                                   Map<String, String> aliasMap) {
        Map<String, ExtractedEntities.PlotThreadSignal> merged = new LinkedHashMap<>();
        if (signals == null) {
            return new ArrayList<>();
        }
        for (ExtractedEntities.PlotThreadSignal signal : signals) {
            if (signal == null || !KgStorySyncUtil.hasMeaningfulText(signal.getThreadTitle())) {
                continue;
            }
            String title = safe(signal.getThreadTitle());
            String signalType = safe(signal.getSignalType());
            if (signalType.isEmpty()) {
                signalType = "ADVANCE";
            }
            merged.put(title + "|" + signalType, ExtractedEntities.PlotThreadSignal.builder()
                    .threadTitle(title)
                    .signalType(signalType)
                    .evidence(safe(signal.getEvidence()))
                    .relatedEvent(resolveAlias(signal.getRelatedEvent(), aliasMap))
                    .summary(safe(signal.getSummary()))
                    .build());
        }
        return new ArrayList<>(merged.values());
    }

    private List<ExtractedEntities.AliasRecord> normalizeAliases(List<ExtractedEntities.AliasRecord> aliases) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ExtractedEntities.AliasRecord> normalized = new ArrayList<>();
        if (aliases == null) {
            return normalized;
        }
        for (ExtractedEntities.AliasRecord alias : aliases) {
            if (alias == null) {
                continue;
            }
            String canonical = safe(alias.getCanonical());
            String aliasName = safe(alias.getAlias());
            if (canonical.isEmpty() || aliasName.isEmpty()) {
                continue;
            }
            String key = canonical + "|" + aliasName;
            if (!seen.add(key)) {
                continue;
            }
            normalized.add(ExtractedEntities.AliasRecord.builder().canonical(canonical).alias(aliasName).build());
        }
        return normalized;
    }

    private String resolveAlias(String raw, Map<String, String> aliasMap) {
        String normalized = safe(raw);
        if (normalized.isEmpty()) {
            return "";
        }
        return aliasMap.getOrDefault(normalized, normalized);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
