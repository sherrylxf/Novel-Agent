package cn.bugstack.novel.domain.service.rag;

import java.util.List;
import java.util.Map;

/**
 * RAG服务接口
 * 用于向量检索，存储文本语义和风格记忆
 *
 * @author xiaofuge bugstack.cn @小傅哥
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
