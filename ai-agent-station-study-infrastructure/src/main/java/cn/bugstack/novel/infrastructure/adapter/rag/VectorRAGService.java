package cn.bugstack.novel.infrastructure.adapter.rag;

import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.IRAGService.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量RAG服务实现
 * 
 * 使用PostgreSQL + pgvector + Spring AI VectorStore
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Service
@Primary
@ConditionalOnBean(name = "vectorStore")
public class VectorRAGService implements IRAGService {
    
    @Autowired(required = false)
    private VectorStore vectorStore;
    
    public VectorRAGService() {
        log.info("VectorRAGService bean 正在创建...");
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        if (vectorStore != null) {
            log.info("VectorRAGService 初始化完成，VectorStore 已注入");
        } else {
            log.warn("VectorRAGService 初始化完成，但 VectorStore 为 null");
        }
    }
    
    @Override
    public void addDocument(String content, String language, Map<String, Object> metadata) {
        if (vectorStore == null) {
            log.warn("VectorStore未配置，文档未添加到向量库");
            return;
        }
        
        try {
            // 合并语言到元数据
            Map<String, Object> finalMetadata = new HashMap<>();
            if (metadata != null) {
                finalMetadata.putAll(metadata);
            }
            finalMetadata.put("language", language);
            
            // 创建Document并添加到向量库
            Document document = new Document(content, finalMetadata);
            vectorStore.add(List.of(document));
            
            log.debug("文档已添加到向量库: 语言={}, 内容长度={}, 元数据={}", 
                language, content != null ? content.length() : 0, finalMetadata);
        } catch (Exception e) {
            log.error("添加文档到向量库失败", e);
            throw new RuntimeException("添加文档到向量库失败", e);
        }
    }
    
    @Override
    public List<SearchResult> search(String query, String language, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore未配置，返回空结果");
            return new ArrayList<>();
        }
        
        try {
            // 构建搜索请求，可以添加语言过滤
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(0.0) // 可以根据需要调整相似度阈值
                    .build();
            
            // 执行搜索
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            // 转换为SearchResult列表
            List<SearchResult> results = documents.stream()
                    .map(doc -> {
                        SearchResult result = new SearchResult();
                        result.setContent(doc.getText()); // 使用 getText() 而不是 getContent()
                        result.setMetadata(doc.getMetadata());
                        // Spring AI的Document可能包含相似度分数，如果有的话
                        if (doc.getMetadata().containsKey("distance")) {
                            // 距离越小相似度越高，转换为分数（0-1之间）
                            Double distance = (Double) doc.getMetadata().get("distance");
                            result.setScore(1.0 - Math.min(distance, 1.0));
                        } else {
                            result.setScore(1.0); // 如果没有距离信息，默认给满分
                        }
                        return result;
                    })
                    .collect(Collectors.toList());
            
            log.debug("向量搜索完成: 查询={}, 语言={}, topK={}, 结果数={}", 
                query, language, topK, results.size());
            
            return results;
        } catch (Exception e) {
            log.error("向量搜索失败", e);
            return new ArrayList<>();
        }
    }
    
}
