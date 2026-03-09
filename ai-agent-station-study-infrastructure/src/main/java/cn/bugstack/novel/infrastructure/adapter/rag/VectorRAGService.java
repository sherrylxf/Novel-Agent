package cn.bugstack.novel.infrastructure.adapter.rag;

import cn.bugstack.novel.domain.model.entity.StoryRetrievalQuery;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.IRAGService.DocumentItem;
import cn.bugstack.novel.domain.service.rag.IRAGService.DocumentListResult;
import cn.bugstack.novel.domain.service.rag.IRAGService.SearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Map<String, Integer> DEFAULT_LAYER_QUOTAS = Map.of(
            "chapter_summary", 1,
            "scene_summary", 1,
            "character_memory", 1,
            "plot_thread_summary", 1,
            "scene_fulltext", 1);

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

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
            Map<String, Object> finalMetadata = new HashMap<>();
            if (metadata != null) {
                finalMetadata.putAll(metadata);
            }
            finalMetadata.put("language", language);

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
        return searchInternal(query, language, topK, null);
    }

    @Override
    public List<SearchResult> searchWithMetadataFilter(String query, String language, int topK,
                                                       Map<String, Object> metadataFilter) {
        return searchInternal(query, language, topK, metadataFilter);
    }

    @Override
    public List<SearchResult> searchByLayer(String queryText, StoryRetrievalQuery retrievalQuery, List<String> memoryTypes) {
        Map<String, Object> filter = new LinkedHashMap<>();
        if (retrievalQuery != null) {
            if (retrievalQuery.getNovelId() != null && !retrievalQuery.getNovelId().isBlank()) {
                filter.put("novelId", retrievalQuery.getNovelId().trim());
            }
            if (retrievalQuery.getCharacters() != null && !retrievalQuery.getCharacters().isEmpty()) {
                filter.put("characters", retrievalQuery.getCharacters());
            }
            if (retrievalQuery.getLocations() != null && !retrievalQuery.getLocations().isEmpty()) {
                filter.put("location", retrievalQuery.getLocations());
            }
            if (retrievalQuery.getPlotThreads() != null && !retrievalQuery.getPlotThreads().isEmpty()) {
                filter.put("plotThreads", retrievalQuery.getPlotThreads());
            }
            if (retrievalQuery.getChapterFrom() != null) {
                filter.put("chapterNumberFrom", retrievalQuery.getChapterFrom());
            }
            if (retrievalQuery.getChapterTo() != null) {
                filter.put("chapterNumberTo", retrievalQuery.getChapterTo());
            }
            if (retrievalQuery.isExplain()) {
                filter.put("explain", true);
            }
            if (retrievalQuery.getExtraFilters() != null && !retrievalQuery.getExtraFilters().isEmpty()) {
                filter.putAll(retrievalQuery.getExtraFilters());
            }
        }
        if (memoryTypes != null && !memoryTypes.isEmpty()) {
            filter.put("memoryType", memoryTypes);
        }
        int topK = retrievalQuery != null && retrievalQuery.getTopK() != null
                ? Math.max(retrievalQuery.getTopK(), 1)
                : 3;
        return searchInternal(queryText, "zh", topK, filter);
    }

    @Override
    public List<SearchResult> searchStoryMemories(StoryRetrievalQuery retrievalQuery) {
        if (retrievalQuery == null || retrievalQuery.getQueryText() == null || retrievalQuery.getQueryText().isBlank()) {
            return new ArrayList<>();
        }
        List<String> memoryTypes = normalizedValues(retrievalQuery.getMemoryTypes());
        if (memoryTypes.isEmpty()) {
            memoryTypes = new ArrayList<>(DEFAULT_LAYER_QUOTAS.keySet());
        }
        Map<String, Integer> quotas = buildLayerQuotas(memoryTypes, retrievalQuery.getTopK());
        List<SearchResult> merged = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : quotas.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            StoryRetrievalQuery layerQuery = copyQuery(retrievalQuery, Math.max(entry.getValue(), retrievalQuery.isExplain() ? 2 : 1));
            List<SearchResult> hits = searchByLayer(retrievalQuery.getQueryText(), layerQuery, List.of(entry.getKey()));
            int limit = Math.min(entry.getValue(), hits.size());
            for (int i = 0; i < limit; i++) {
                merged.add(hits.get(i));
            }
        }
        List<SearchResult> deduped = deduplicateAndSort(merged);
        int topK = retrievalQuery.getTopK() != null ? Math.max(retrievalQuery.getTopK(), 1) : 3;
        return deduped.size() > topK ? new ArrayList<>(deduped.subList(0, topK)) : deduped;
    }

    @Override
    public DocumentListResult listDocuments(String novelId, String chapterId, int page, int size) {
        return listDocuments(novelId, chapterId, page, size, null);
    }

    @Override
    public DocumentListResult listDocuments(String novelId, String chapterId, int page, int size,
                                            Map<String, Object> metadataFilter) {
        DocumentListResult result = new DocumentListResult();
        result.setList(new ArrayList<>());
        result.setTotal(0);
        if (pgVectorJdbcTemplate == null) {
            log.warn("pgVectorJdbcTemplate未配置，返回空列表");
            return result;
        }
        try {
            Map<String, Object> filter = new LinkedHashMap<>();
            if (metadataFilter != null) {
                filter.putAll(metadataFilter);
            }
            if (novelId != null && !novelId.isEmpty()) {
                filter.put("novelId", novelId);
            }
            if (chapterId != null && !chapterId.isEmpty()) {
                filter.put("chapterId", chapterId);
            }
            SqlWhereClause clause = buildWhereClause(filter);

            String countSql = "SELECT COUNT(*) FROM " + VECTOR_TABLE + clause.sql();
            Long total = pgVectorJdbcTemplate.queryForObject(countSql, Long.class, clause.args().toArray());
            result.setTotal(total != null ? total : 0);

            int offset = (page <= 0 ? 1 : page - 1) * (size <= 0 ? 20 : size);
            int limit = size <= 0 ? 20 : Math.min(size, 500);
            List<Object> listArgs = new ArrayList<>(clause.args());
            listArgs.add(limit);
            listArgs.add(offset);
            String listSql = "SELECT id, content, metadata FROM " + VECTOR_TABLE + clause.sql()
                    + " ORDER BY COALESCE(NULLIF(metadata->>'chapterNumber', '')::int, 0) DESC, id DESC LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = pgVectorJdbcTemplate.queryForList(listSql, listArgs.toArray());
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
    public DocumentItem getDocumentById(String id) {
        if (pgVectorJdbcTemplate == null || id == null || id.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = pgVectorJdbcTemplate.queryForList(
                    "SELECT id, content, metadata FROM " + VECTOR_TABLE + " WHERE id = ?::uuid",
                    id);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            DocumentItem item = new DocumentItem();
            item.setId(row.get("id") != null ? row.get("id").toString() : null);
            String content = row.get("content") != null ? row.get("content").toString() : "";
            item.setContent(content);
            item.setContentSummary(content.length() > 300 ? content.substring(0, 300) + "..." : content);
            item.setMetadata(toMap(row.get("metadata")));
            return item;
        } catch (Exception e) {
            log.error("getDocumentById 失败, id={}", id, e);
            return null;
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

    private List<SearchResult> searchInternal(String query, String language, int topK, Map<String, Object> metadataFilter) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<SearchResult> results = searchWithSql(query, topK, metadataFilter);
            if (results.isEmpty()) {
                results = searchWithVectorStore(query, topK, metadataFilter);
            }
            results = deduplicateAndSort(results);
            if (results.size() > topK) {
                results = new ArrayList<>(results.subList(0, topK));
            }
            log.debug("向量搜索完成: 查询={}, 语言={}, topK={}, 结果数={}",
                    query, language, topK, results.size());
            return results;
        } catch (org.springframework.ai.retry.NonTransientAiException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("404")) {
                    log.warn("Embeddings API不可用（404错误），返回空结果，应用将继续运行但不使用RAG功能。");
                } else if (errorMsg.contains("429") || errorMsg.contains("余额不足") || errorMsg.contains("1113")) {
                    log.warn("Embeddings API余额不足（429错误），返回空结果，应用将继续运行但不使用RAG功能。");
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

    private List<SearchResult> searchWithSql(String query, int topK, Map<String, Object> metadataFilter) {
        if (pgVectorJdbcTemplate == null || embeddingModel == null) {
            return new ArrayList<>();
        }
        try {
            float[] embedding = embeddingModel.embed(new Document(query, Map.of()));
            if (embedding == null || embedding.length == 0) {
                return new ArrayList<>();
            }
            SqlWhereClause clause = buildWhereClause(metadataFilter);
            List<Object> args = new ArrayList<>();
            String vectorLiteral = toVectorLiteral(embedding);
            args.add(vectorLiteral);
            args.addAll(clause.args());
            args.add(vectorLiteral);
            args.add(Math.max(topK * 4, 12));
            String sql = "SELECT id, content, metadata, (1 - (embedding <=> CAST(? AS vector))) AS similarity " +
                    "FROM " + VECTOR_TABLE + clause.sql() +
                    " ORDER BY embedding <=> CAST(? AS vector) LIMIT ?";
            List<Map<String, Object>> rows = pgVectorJdbcTemplate.queryForList(sql, args.toArray());
            Integer currentChapter = extractCurrentChapter(metadataFilter);
            boolean explain = isExplain(metadataFilter);
            List<SearchResult> results = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> metadata = toMap(row.get("metadata"));
                SearchResult result = new SearchResult();
                result.setId(row.get("id") != null ? row.get("id").toString() : null);
                result.setContent(row.get("content") != null ? row.get("content").toString() : "");
                result.setMetadata(metadata);
                applyScores(result, toDouble(row.get("similarity"), 0.0), currentChapter, explain);
                results.add(result);
            }
            return results;
        } catch (Exception e) {
            log.warn("SQL 向量检索失败，回退到 VectorStore: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SearchResult> searchWithVectorStore(String query, int topK, Map<String, Object> metadataFilter) {
        if (vectorStore == null) {
            log.warn("VectorStore未配置，返回空结果");
            return new ArrayList<>();
        }
        int fetchTopK = (metadataFilter != null && !metadataFilter.isEmpty())
                ? Math.max(topK * 4, 24) : Math.max(topK, 10);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(fetchTopK)
                .similarityThreshold(0.0)
                .build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        Integer currentChapter = extractCurrentChapter(metadataFilter);
        boolean explain = isExplain(metadataFilter);
        List<SearchResult> results = new ArrayList<>();
        for (Document doc : documents) {
            SearchResult result = new SearchResult();
            result.setContent(doc.getText());
            result.setMetadata(doc.getMetadata());
            double similarity = 1.0;
            if (doc.getMetadata() != null && doc.getMetadata().containsKey("distance")) {
                similarity = 1.0 - Math.min(toDouble(doc.getMetadata().get("distance"), 1.0), 1.0);
            }
            applyScores(result, similarity, currentChapter, explain);
            results.add(result);
        }
        if (metadataFilter != null && !metadataFilter.isEmpty()) {
            results = postFilterByMetadata(results, metadataFilter, topK);
        }
        return results;
    }

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
                // ignore
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

    private void applyScores(SearchResult result, double similarityScore, Integer currentChapter, boolean explain) {
        Map<String, Object> metadata = result.getMetadata() != null ? result.getMetadata() : new HashMap<>();
        Integer chapterNumber = toInteger(metadata.get("chapterNumber"));
        double recencyScore = computeRecencyScore(currentChapter, chapterNumber);
        double finalScore = similarityScore * 0.7 + recencyScore * 0.3;
        result.setSimilarityScore(roundScore(similarityScore));
        result.setRecencyScore(roundScore(recencyScore));
        result.setFinalScore(roundScore(finalScore));
        result.setScore(result.getFinalScore());
        if (explain) {
            Map<String, Object> explainMap = new LinkedHashMap<>();
            explainMap.put("similarityScore", result.getSimilarityScore());
            explainMap.put("recencyScore", result.getRecencyScore());
            explainMap.put("finalScore", result.getFinalScore());
            explainMap.put("chapterNumber", chapterNumber);
            explainMap.put("memoryType", metadata.get("memoryType"));
            result.setExplain(explainMap);
        }
    }

    private Map<String, Integer> buildLayerQuotas(List<String> memoryTypes, Integer requestedTopK) {
        LinkedHashMap<String, Integer> quotas = new LinkedHashMap<>();
        for (String type : memoryTypes) {
            quotas.put(type, DEFAULT_LAYER_QUOTAS.getOrDefault(type, 1));
        }
        int target = requestedTopK != null ? Math.max(requestedTopK, 1) : 3;
        int sum = quotas.values().stream().mapToInt(Integer::intValue).sum();
        if (sum >= target) {
            return quotas;
        }
        List<String> orderedTypes = new ArrayList<>(quotas.keySet());
        int index = 0;
        while (sum < target && !orderedTypes.isEmpty()) {
            String type = orderedTypes.get(index % orderedTypes.size());
            quotas.put(type, quotas.get(type) + 1);
            sum++;
            index++;
        }
        return quotas;
    }

    private StoryRetrievalQuery copyQuery(StoryRetrievalQuery source, int topK) {
        return StoryRetrievalQuery.builder()
                .novelId(source.getNovelId())
                .queryText(source.getQueryText())
                .characters(new ArrayList<>(normalizedValues(source.getCharacters())))
                .locations(new ArrayList<>(normalizedValues(source.getLocations())))
                .topics(new ArrayList<>(normalizedValues(source.getTopics())))
                .plotThreads(new ArrayList<>(normalizedValues(source.getPlotThreads())))
                .memoryTypes(new ArrayList<>(normalizedValues(source.getMemoryTypes())))
                .chapterFrom(source.getChapterFrom())
                .chapterTo(source.getChapterTo())
                .topK(topK)
                .explain(source.isExplain())
                .extraFilters(source.getExtraFilters() != null ? new LinkedHashMap<>(source.getExtraFilters()) : new LinkedHashMap<>())
                .build();
    }

    private List<SearchResult> deduplicateAndSort(List<SearchResult> results) {
        LinkedHashMap<String, SearchResult> best = new LinkedHashMap<>();
        for (SearchResult result : results) {
            String key = dedupeKey(result);
            SearchResult existing = best.get(key);
            if (existing == null || scoreOf(result) > scoreOf(existing)) {
                best.put(key, result);
            }
        }
        List<SearchResult> deduped = new ArrayList<>(best.values());
        deduped.sort(Comparator.comparingDouble(this::scoreOf).reversed());
        return deduped;
    }

    private double scoreOf(SearchResult result) {
        if (result == null) {
            return 0.0;
        }
        if (result.getFinalScore() != null) {
            return result.getFinalScore();
        }
        return result.getScore() != null ? result.getScore() : 0.0;
    }

    private String dedupeKey(SearchResult result) {
        Map<String, Object> metadata = result != null ? result.getMetadata() : null;
        if (metadata != null) {
            Object sceneId = metadata.get("sceneId");
            if (sceneId != null && !sceneId.toString().isBlank()) {
                return "scene:" + sceneId;
            }
            List<String> plotThreadIds = normalizedValues(toList(metadata.get("plotThreadIds")));
            if (!plotThreadIds.isEmpty()) {
                return "plot:" + plotThreadIds.get(0);
            }
            Object chapterId = metadata.get("chapterId");
            Object memoryType = metadata.get("memoryType");
            if (chapterId != null && memoryType != null) {
                return "chapter:" + chapterId + ":" + memoryType;
            }
        }
        if (result != null && result.getId() != null) {
            return "id:" + result.getId();
        }
        return "content:" + (result != null && result.getContent() != null ? result.getContent().hashCode() : 0);
    }

    private SqlWhereClause buildWhereClause(Map<String, Object> filter) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (filter == null || filter.isEmpty()) {
            return new SqlWhereClause(where.toString(), args);
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (key == null || key.isBlank() || val == null || "explain".equals(key)) {
                continue;
            }
            switch (key) {
                case "chapterNumberFrom" -> {
                    where.append(" AND NULLIF(metadata->>'chapterNumber', '') IS NOT NULL AND (metadata->>'chapterNumber')::int >= ? ");
                    args.add(toInteger(val));
                }
                case "chapterNumberTo" -> {
                    where.append(" AND NULLIF(metadata->>'chapterNumber', '') IS NOT NULL AND (metadata->>'chapterNumber')::int <= ? ");
                    args.add(toInteger(val));
                }
                case "memoryType" -> appendExactCondition(where, args, "memoryType", val);
                case "novelId", "chapterId", "sceneId" -> appendExactCondition(where, args, key, val);
                case "location" -> appendContainsCondition(where, args, "location", val, false);
                case "characters", "plotThreads", "events", "characterIds", "eventIds", "plotThreadIds", "foreshadowing" ->
                        appendContainsCondition(where, args, key, val, true);
                default -> appendContainsCondition(where, args, key, val, true);
            }
        }
        return new SqlWhereClause(where.toString(), args);
    }

    private void appendExactCondition(StringBuilder where, List<Object> args, String key, Object value) {
        List<String> values = normalizedValues(toList(value));
        if (values.isEmpty()) {
            return;
        }
        if (values.size() == 1) {
            where.append(" AND metadata->>'").append(key).append("' = ? ");
            args.add(values.get(0));
            return;
        }
        where.append(" AND metadata->>'").append(key).append("' IN (")
                .append(String.join(",", java.util.Collections.nCopies(values.size(), "?")))
                .append(") ");
        args.addAll(values);
    }

    private void appendContainsCondition(StringBuilder where, List<Object> args, String key, Object value, boolean alsoSearchWholeMetadata) {
        List<String> values = normalizedValues(toList(value));
        if (values.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String item : values) {
            if (alsoSearchWholeMetadata) {
                parts.add("(LOWER(COALESCE(metadata->>'" + key + "', '')) LIKE ? OR LOWER(CAST(metadata AS TEXT)) LIKE ?)");
                String keyword = "%" + item.toLowerCase(Locale.ROOT) + "%";
                args.add(keyword);
                args.add(keyword);
            } else {
                parts.add("LOWER(COALESCE(metadata->>'" + key + "', '')) LIKE ?");
                args.add("%" + item.toLowerCase(Locale.ROOT) + "%");
            }
        }
        where.append(" AND (").append(String.join(" OR ", parts)).append(") ");
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
            String key = entry.getKey();
            Object filterVal = entry.getValue();
            if (filterVal == null || "explain".equals(key)) {
                continue;
            }
            if ("chapterNumberFrom".equals(key)) {
                Integer chapterNumber = toInteger(docMeta.get("chapterNumber"));
                if (chapterNumber == null || chapterNumber < toInteger(filterVal)) return false;
                continue;
            }
            if ("chapterNumberTo".equals(key)) {
                Integer chapterNumber = toInteger(docMeta.get("chapterNumber"));
                if (chapterNumber == null || chapterNumber > toInteger(filterVal)) return false;
                continue;
            }
            Object docVal = docMeta.get(key);
            if (filterVal instanceof List<?> filterList) {
                if (!matchesAny(docVal, filterList)) {
                    return false;
                }
            } else if (!matchesSingle(docVal, filterVal)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesAny(Object docVal, List<?> filterList) {
        if (filterList == null || filterList.isEmpty()) {
            return true;
        }
        for (Object item : filterList) {
            if (matchesSingle(docVal, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSingle(Object docVal, Object filterVal) {
        if (filterVal == null) {
            return true;
        }
        if (docVal == null) {
            return false;
        }
        String expected = filterVal.toString().trim().toLowerCase(Locale.ROOT);
        if (expected.isEmpty()) {
            return true;
        }
        if (docVal instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && item.toString().trim().toLowerCase(Locale.ROOT).contains(expected)) {
                    return true;
                }
            }
            return false;
        }
        String actual = docVal.toString().trim().toLowerCase(Locale.ROOT);
        return actual.equals(expected) || actual.contains(expected);
    }

    private Integer extractCurrentChapter(Map<String, Object> metadataFilter) {
        if (metadataFilter == null) {
            return null;
        }
        Integer current = toInteger(metadataFilter.get("chapterNumberTo"));
        if (current != null) {
            return current;
        }
        return toInteger(metadataFilter.get("chapterNumber"));
    }

    private boolean isExplain(Map<String, Object> metadataFilter) {
        if (metadataFilter == null) {
            return false;
        }
        Object explain = metadataFilter.get("explain");
        return explain instanceof Boolean ? (Boolean) explain : Boolean.parseBoolean(String.valueOf(explain));
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private double computeRecencyScore(Integer currentChapter, Integer chapterNumber) {
        if (currentChapter == null || currentChapter <= 0 || chapterNumber == null || chapterNumber <= 0) {
            return 0.5;
        }
        double gap = Math.abs(currentChapter - chapterNumber);
        double normalized = Math.min(gap / Math.max(currentChapter, 1), 1.0);
        return 1.0 - normalized;
    }

    private double roundScore(double score) {
        return Math.round(score * 10000.0) / 10000.0;
    }

    private double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !value.toString().isBlank()) {
            try {
                return Integer.parseInt(value.toString());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Object> toList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of(value);
    }

    private List<String> normalizedValues(List<?> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                normalized.add(text);
            }
        }
        return new ArrayList<>(normalized);
    }

    private record SqlWhereClause(String sql, List<Object> args) {}
}
