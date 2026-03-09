package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 从章节/场景正文中抽取的结构化实体
 * 供信息抽取 Agent 输出，用于更新 KG 与 RAG
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedEntities {

    @Builder.Default
    private List<String> characters = new ArrayList<>();

    @Builder.Default
    private List<String> locations = new ArrayList<>();

    @Builder.Default
    private List<String> factions = new ArrayList<>();

    @Builder.Default
    private List<String> artifacts = new ArrayList<>();

    @Builder.Default
    private List<String> techniques = new ArrayList<>();

    @Builder.Default
    private List<EventRecord> events = new ArrayList<>();

    @Builder.Default
    private List<RelationTriple> relations = new ArrayList<>();

    @Builder.Default
    private List<String> foreshadowing = new ArrayList<>();

    @Builder.Default
    private List<StateChange> stateChanges = new ArrayList<>();

    @Builder.Default
    private List<PlotThreadSignal> plotThreadSignals = new ArrayList<>();

    @Builder.Default
    private List<AliasRecord> aliases = new ArrayList<>();

    /**
     * 关系三元组：主体 - 关系类型 - 客体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationTriple {
        private String subject;
        private String relationType;
        private String object;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventRecord {
        private String name;
        private String eventType;
        private String summary;
        private String location;
        private String outcome;
        private Double importance;
        @Builder.Default
        private List<EventParticipant> participants = new ArrayList<>();
        @Builder.Default
        private List<String> factions = new ArrayList<>();
        @Builder.Default
        private List<String> relatedThreads = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventParticipant {
        private String name;
        private String entityType;
        private String role;
        private String outcome;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateChange {
        private String entityType;
        private String name;
        private String field;
        private String oldValue;
        private String newValue;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlotThreadSignal {
        private String threadTitle;
        private String signalType;
        private String evidence;
        private String relatedEvent;
        private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AliasRecord {
        private String canonical;
        private String alias;
    }
}
