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
    private List<String> events = new ArrayList<>();

    @Builder.Default
    private List<RelationTriple> relations = new ArrayList<>();

    @Builder.Default
    private List<String> foreshadowing = new ArrayList<>();

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
}
