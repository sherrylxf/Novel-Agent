package cn.bugstack.novel.domain.service.rag;

import java.util.List;
import java.util.Map;

/**
 * RAG服务接口
 * 用于向量检索，存储文本语义和风格记忆
 */
public interface IRAGService {
    
    /**
     * 添加文档到向量库
     *
     * @param content 文档内容
     * @param metadata 元数据（语言、风格、类型等）
     */
    void addDocument(String content, String language, Map<String, Object> metadata);
    
    /**
     * 相似度检索
     *
     * @param query 查询文本
     * @param language 语言
     * @param topK 返回数量
     * @return 相似文档列表
     */
    List<SearchResult> search(String query, String language, int topK);

    /**
     * 相似度检索（可选按小说ID过滤，仅返回该小说的文档，用于生成时避免跨小说干扰）
     *
     * @param query 查询文本
     * @param language 语言
     * @param topK 返回数量
     * @param novelId 小说ID，为空时不过滤
     * @return 相似文档列表
     */
    default List<SearchResult> search(String query, String language, int topK, String novelId) {
        List<SearchResult> all = search(query, language, novelId != null && !novelId.isEmpty() ? topK * 2 : topK);
        if (novelId == null || novelId.isEmpty()) return all;
        List<SearchResult> filtered = new java.util.ArrayList<>();
        for (SearchResult r : all) {
            Object mid = r.getMetadata() != null ? r.getMetadata().get("novelId") : null;
            if (novelId.equals(mid != null ? mid.toString() : null)) {
                filtered.add(r);
                if (filtered.size() >= topK) break;
            }
        }
        return filtered;
    }

    /**
     * 相似度检索（按小说ID与记忆类型过滤）。
     * memoryType 典型值：
     * chapter_summary / scene_summary / scene_fulltext / character_profile
     */
    default List<SearchResult> search(String query, String language, int topK, String novelId, List<String> memoryTypes) {
        List<SearchResult> all = search(query, language, Math.max(topK * 4, 12), novelId);
        if (memoryTypes == null || memoryTypes.isEmpty()) {
            return all.size() > topK ? all.subList(0, topK) : all;
        }
        java.util.Set<String> typeSet = new java.util.HashSet<>();
        for (String memoryType : memoryTypes) {
            if (memoryType != null && !memoryType.isBlank()) {
                typeSet.add(memoryType.trim());
            }
        }
        List<SearchResult> filtered = new java.util.ArrayList<>();
        for (SearchResult result : all) {
            Object memoryType = result.getMetadata() != null ? result.getMetadata().get("memoryType") : null;
            if (memoryType != null && typeSet.contains(memoryType.toString())) {
                filtered.add(result);
                if (filtered.size() >= topK) {
                    break;
                }
            }
        }
        return filtered;
    }

    /**
     * 相似度检索（带 metadata 过滤，在向量检索时即应用过滤，而非召回后筛选）。
     *
     * @param query        查询文本
     * @param language     语言
     * @param topK         返回数量
     * @param metadataFilter 元数据过滤条件，key 为 metadata 字段名，value 为期望值或 List 表示 IN 匹配。
     *                      例如：{"novelId":"xxx","memoryType":["chapter_summary","scene_summary"]}
     * @return 相似文档列表
     */
    default List<SearchResult> searchWithMetadataFilter(String query, String language, int topK,
                                                        Map<String, Object> metadataFilter) {
        return search(query, language, topK);
    }

    /**
     * 分页列出文档（按 novelId、可选 chapterId 筛选）
     *
     * @param novelId 小说ID，可为空表示不过滤
     * @param chapterId 章节ID，可为空
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 文档列表与总数
     */
    DocumentListResult listDocuments(String novelId, String chapterId, int page, int size);

    /**
     * 按主键删除一条文档
     */
    void deleteById(String id);

    /**
     * 按小说ID删除该小说下所有向量文档
     */
    void deleteByNovelId(String novelId);

    /**
     * 文档列表结果
     */
    class DocumentListResult {
        private List<DocumentItem> list;
        private long total;
        public List<DocumentItem> getList() { return list; }
        public void setList(List<DocumentItem> list) { this.list = list; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
    }

    /**
     * 文档项（列表用）
     */
    class DocumentItem {
        private String id;
        private String contentSummary;
        private Map<String, Object> metadata;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContentSummary() { return contentSummary; }
        public void setContentSummary(String contentSummary) { this.contentSummary = contentSummary; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * 检索结果
     */
    class SearchResult {
        private String content;
        private Double score;
        private Map<String, Object> metadata;
        
        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
}
