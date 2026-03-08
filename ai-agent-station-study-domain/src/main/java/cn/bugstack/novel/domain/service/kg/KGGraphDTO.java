package cn.bugstack.novel.domain.service.kg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱图数据 DTO，供前端可视化（节点+边）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KGGraphDTO {

    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {
        private String id;
        private String label;
        private String type;   // Character | Foreshadowing
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {
        private String id;
        private String source;
        private String target;
        private String type;   // 关系类型，如 CP、仇敌、师徒
        private Map<String, Object> properties;
    }
}
