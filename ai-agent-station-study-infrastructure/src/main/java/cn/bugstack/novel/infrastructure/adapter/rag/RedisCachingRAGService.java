package cn.bugstack.novel.infrastructure.adapter.rag;

import cn.bugstack.novel.domain.model.entity.StoryRetrievalQuery;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.util.DigestUtils;

/**
 * 对 {@link VectorRAGService} 的检索热点缓存（Redis），减少重复向量检索与上下文拼装开销。
 */
@Slf4j
@Service
@Primary
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisCachingRAGService implements IRAGService {

    private static final String CACHE_PREFIX = "novel:rag:sm:";
    private static final int DEFAULT_TTL_SECONDS = 300;

    private final IRAGService delegate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisCachingRAGService(@Qualifier("vectorRAGServiceDelegate") IRAGService delegate,
                                  StringRedisTemplate stringRedisTemplate) {
        this.delegate = delegate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void addDocument(String content, String language, Map<String, Object> metadata) {
        delegate.addDocument(content, language, metadata);
    }

    @Override
    public List<SearchResult> search(String query, String language, int topK) {
        return delegate.search(query, language, topK);
    }

    @Override
    public List<SearchResult> searchWithMetadataFilter(String query, String language, int topK,
                                                       Map<String, Object> metadataFilter) {
        return delegate.searchWithMetadataFilter(query, language, topK, metadataFilter);
    }

    @Override
    public List<SearchResult> searchByLayer(String queryText, StoryRetrievalQuery retrievalQuery, List<String> memoryTypes) {
        return delegate.searchByLayer(queryText, retrievalQuery, memoryTypes);
    }

    @Override
    public List<SearchResult> searchStoryMemories(StoryRetrievalQuery retrievalQuery) {
        if (retrievalQuery == null) {
            return delegate.searchStoryMemories(null);
        }
        String key = CACHE_PREFIX + cacheKey(retrievalQuery);
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached, new TypeReference<List<SearchResult>>() {});
            }
        } catch (Exception e) {
            log.warn("RAG 缓存反序列化失败，回源: {}", e.getMessage());
        }
        List<SearchResult> list = delegate.searchStoryMemories(retrievalQuery);
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(list),
                    DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("写入 RAG 缓存失败: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public DocumentListResult listDocuments(String novelId, String chapterId, int page, int size) {
        return delegate.listDocuments(novelId, chapterId, page, size);
    }

    @Override
    public DocumentListResult listDocuments(String novelId, String chapterId, int page, int size,
                                            Map<String, Object> metadataFilter) {
        return delegate.listDocuments(novelId, chapterId, page, size, metadataFilter);
    }

    @Override
    public void deleteById(String id) {
        delegate.deleteById(id);
    }

    @Override
    public DocumentItem getDocumentById(String id) {
        return delegate.getDocumentById(id);
    }

    @Override
    public void deleteByNovelId(String novelId) {
        delegate.deleteByNovelId(novelId);
    }

    private String cacheKey(StoryRetrievalQuery q) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("novelId", q.getNovelId());
            m.put("queryText", q.getQueryText());
            m.put("characters", q.getCharacters());
            m.put("locations", q.getLocations());
            m.put("plotThreads", q.getPlotThreads());
            m.put("memoryTypes", q.getMemoryTypes());
            m.put("chapterFrom", q.getChapterFrom());
            m.put("chapterTo", q.getChapterTo());
            m.put("topK", q.getTopK());
            m.put("explain", q.isExplain());
            m.put("extraFilters", q.getExtraFilters());
            String raw = objectMapper.writeValueAsString(m);
            return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return String.valueOf(q.hashCode());
        }
    }
}
