package cn.bugstack.novel.infrastructure.adapter.rag;

import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.IRAGService.DocumentItem;
import cn.bugstack.novel.domain.service.rag.IRAGService.DocumentListResult;
import cn.bugstack.novel.domain.service.rag.IRAGService.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量RAG服务实现
 * 
 * 使用PostgreSQL + pgvector + Spring AI VectorStore
 */
@Slf4j
@Service
@Primary
@ConditionalOnBean(name = "vectorStore")
public class VectorRAGService implements IRAGService {
    
    private static final String VECTOR_TABLE = "novel_vector_store";

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgVectorJdbcTemplate;
    
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
            
            log.info("RAG存储: 文档已写入向量库(PgVector), 语言={}, 内容长度={}, novelId={}", 
                language, content != null ? content.length() : 0, finalMetadata.get("novelId"));
        } catch (Exception e) {
            log.error("添加文档到向量库失败", e);
            throw new RuntimeException("添加文档到向量库失败", e);
        }
    }
    
    @Override
    public List<SearchResult> search(String query, String language, int topK) {
        return searchInternal(query, language, topK * 2, null);
    }

    @Override
    public List<SearchResult> searchWithMetadataFilter(String query, String language, int topK,
                                                       Map<String, Object> metadataFilter) {
        return searchInternal(query, language, topK, metadataFilter);
    }

    @Override
    public List<SearchResult> search(String query, String language, int topK, String novelId) {
        Map<String, Object> filter = (novelId != null && !novelId.isEmpty())
                ? Map.of("novelId", novelId)
                : null;
        List<SearchResult> raw = searchInternal(query, language, novelId != null && !novelId.isEmpty() ? topK * 3 : topK, filter);
        if (novelId == null || novelId.isEmpty()) {
            return raw.size() > topK ? raw.subList(0, topK) : raw;
        }
        return raw;
    }

    private List<SearchResult> searchInternal(String query, String language, int topK, Map<String, Object> metadataFilter) {
        if (vectorStore == null) {
            log.warn("VectorStore未配置，返回空结果");
            return new ArrayList<>();
        }
        
        try {
            int fetchTopK = (metadataFilter != null && !metadataFilter.isEmpty())
                    ? Math.max(topK * 4, 24) : Math.max(topK, 10);
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(fetchTopK)
                    .similarityThreshold(0.0)
                    .build();
            
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            List<SearchResult> results = documents.stream()
                    .map(doc -> {
                        SearchResult result = new SearchResult();
                        result.setContent(doc.getText());
                        result.setMetadata(doc.getMetadata());
                        if (doc.getMetadata().containsKey("distance")) {
                            Object distObj = doc.getMetadata().get("distance");
                            double distance = distObj instanceof Number ? ((Number) distObj).doubleValue() : 1.0;
                            result.setScore(1.0 - Math.min(distance, 1.0));
                        } else {
                            result.setScore(1.0);
                        }
                        return result;
                    })
                    .collect(Collectors.toList());
            if (metadataFilter != null && !metadataFilter.isEmpty()) {
                results = postFilterByMetadata(results, metadataFilter, topK);
            }
            log.debug("向量搜索完成: 查询={}, 语言={}, topK={}, 结果数={}", 
                query, language, topK, results.size());
            return results;
        } catch (org.springframework.ai.retry.NonTransientAiException e) {
            // 处理API不可用的情况
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("404")) {
                    log.warn("Embeddings API不可用（404错误），可能是DeepSeek不支持embeddings API。返回空结果，应用将继续运行但不使用RAG功能。");
                    log.warn("建议：1) 使用其他支持embeddings的API提供商（如OpenAI、Qwen、智谱AI等）");
                    log.warn("     2) 或者暂时禁用RAG功能");
                } else if (errorMsg.contains("429") || errorMsg.contains("余额不足") || errorMsg.contains("1113")) {
                    log.warn("Embeddings API余额不足（429错误），返回空结果，应用将继续运行但不使用RAG功能。");
                    log.warn("建议：1) 检查智谱AI账户余额并充值");
                    log.warn("     2) 或者使用其他支持embeddings的API提供商（如OpenAI、Qwen等）");
                    log.warn("     3) 或者使用本地embedding模型（如Ollama）");
                    log.warn("     4) 或者暂时禁用RAG功能（应用仍可正常运行，但不会使用向量检索）");
                } else {
                    log.error("向量搜索失败（NonTransientAiException）", e);
                }
            } else {
                log.error("向量搜索失败（NonTransientAiException）", e);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("向量搜索失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public DocumentListResult listDocuments(String novelId, String chapterId, int page, int size) {
        DocumentListResult result = new DocumentListResult();
        result.setList(new ArrayList<>());
        result.setTotal(0);
        if (pgVectorJdbcTemplate == null) {
            log.warn("pgVectorJdbcTemplate未配置，返回空列表");
            return result;
        }
        try {
            StringBuilder where = new StringBuilder(" WHERE 1=1 ");
            List<Object> args = new ArrayList<>();
            if (novelId != null && !novelId.isEmpty()) {
                where.append(" AND metadata->>'novelId' = ? ");
                args.add(novelId);
            }
            if (chapterId != null && !chapterId.isEmpty()) {
                where.append(" AND metadata->>'chapterId' = ? ");
                args.add(chapterId);
            }
            String countSql = "SELECT COUNT(*) FROM " + VECTOR_TABLE + where;
            Long total = pgVectorJdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
            result.setTotal(total != null ? total : 0);

            int offset = (page <= 0 ? 1 : page - 1) * (size <= 0 ? 20 : size);
            int limit = size <= 0 ? 20 : Math.min(size, 500);
            String listSql = "SELECT id, content, metadata FROM " + VECTOR_TABLE + where + " ORDER BY id LIMIT ? OFFSET ? ";
            args.add(limit);
            args.add(offset);
            List<Map<String, Object>> rows = pgVectorJdbcTemplate.queryForList(listSql, args.toArray());
            for (Map<String, Object> row : rows) {
                DocumentItem item = new DocumentItem();
                Object id = row.get("id");
                item.setId(id != null ? id.toString() : null);
                String content = row.get("content") != null ? row.get("content").toString() : "";
                item.setContentSummary(content.length() > 300 ? content.substring(0, 300) + "..." : content);
                item.setMetadata(toMap(row.get("metadata")));
                result.getList().add(item);
            }
        } catch (Exception e) {
            log.error("listDocuments 失败", e);
        }
        return result;
    }

    @Override
    public void deleteById(String id) {
        if (pgVectorJdbcTemplate == null) return;
        try {
            pgVectorJdbcTemplate.update("DELETE FROM " + VECTOR_TABLE + " WHERE id = ?::uuid", id);
            log.info("RAG 删除文档: id={}", id);
        } catch (Exception e) {
            log.error("deleteById 失败, id={}", id, e);
            throw new RuntimeException("删除文档失败", e);
        }
    }

    @Override
    public void deleteByNovelId(String novelId) {
        if (pgVectorJdbcTemplate == null) return;
        if (novelId == null || novelId.isEmpty()) return;
        try {
            int n = pgVectorJdbcTemplate.update("DELETE FROM " + VECTOR_TABLE + " WHERE metadata->>'novelId' = ?", novelId);
            log.info("RAG 按小说删除文档: novelId={}, 删除条数={}", novelId, n);
        } catch (Exception e) {
            log.error("deleteByNovelId 失败, novelId={}", novelId, e);
            throw new RuntimeException("按小说删除文档失败", e);
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object metadata) {
        if (metadata == null) return new HashMap<>();
        if (metadata instanceof Map) return (Map<String, Object>) metadata;
        String json = null;
        if (metadata instanceof String) {
            json = (String) metadata;
        } else {
            try {
                java.lang.reflect.Method m = metadata.getClass().getMethod("getValue");
                Object v = m.invoke(metadata);
                if (v instanceof String) json = (String) v;
            } catch (Exception ignored) {
                // PGobject.getValue() 或其它可返回 JSON 字符串的类型
            }
        }
        if (json != null && !json.isEmpty()) {
            try {
                return JSON_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.trace("metadata JSON 解析失败，返回空 Map: {}", e.getMessage());
            }
        }
        return new HashMap<>();
    }

    /**
     * 构建 Spring AI Filter 表达式（metadata 过滤）
     * 格式：novelId == 'xxx' && memoryType in ['a','b']
     */
    private String buildFilterExpression(Map<String, Object> metadataFilter) {
        if (metadataFilter == null || metadataFilter.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : metadataFilter.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (key == null || key.isBlank() || val == null) continue;
            String part;
            if (val instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) val;
                if (list.isEmpty()) continue;
                List<String> escaped = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) escaped.add("'" + escapeFilterValue(String.valueOf(item)) + "'");
                }
                if (escaped.isEmpty()) continue;
                part = key + " in [" + String.join(",", escaped) + "]";
            } else {
                part = key + " == '" + escapeFilterValue(String.valueOf(val)) + "'";
            }
            parts.add(part);
        }
        return parts.isEmpty() ? null : String.join(" && ", parts);
    }

    private static String escapeFilterValue(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }

    private List<SearchResult> postFilterByMetadata(List<SearchResult> results,
                                                     Map<String, Object> metadataFilter, int topK) {
        if (metadataFilter == null || metadataFilter.isEmpty()) return results;
        List<SearchResult> filtered = new ArrayList<>();
        for (SearchResult r : results) {
            if (matchesMetadataFilter(r.getMetadata(), metadataFilter)) {
                filtered.add(r);
                if (filtered.size() >= topK) break;
            }
        }
        return filtered;
    }

    private boolean matchesMetadataFilter(Map<String, Object> docMeta, Map<String, Object> filter) {
        if (docMeta == null || filter == null) return filter == null || filter.isEmpty();
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object filterVal = entry.getValue();
            Object docVal = docMeta.get(entry.getKey());
            if (filterVal instanceof List) {
                if (docVal == null || !((List<?>) filterVal).contains(docVal.toString())) return false;
            } else {
                if (docVal == null || !filterVal.toString().equals(docVal.toString())) return false;
            }
        }
        return true;
    }
    
}
