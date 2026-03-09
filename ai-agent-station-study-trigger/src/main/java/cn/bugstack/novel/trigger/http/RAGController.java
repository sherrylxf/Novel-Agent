package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.domain.model.entity.StoryRetrievalQuery;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档库 HTTP 接口：文档列表、检索、删除
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class RAGController {

    @Resource
    private IRAGService ragService;

    /**
     * 分页查询文档列表
     * GET /api/rag/documents?novelId=xxx&chapterId=xxx&page=1&size=20
     */
    @GetMapping("/documents")
    public Map<String, Object> listDocuments(
            @RequestParam(value = "novelId", required = false) String novelId,
            @RequestParam(value = "chapterId", required = false) String chapterId,
            @RequestParam(value = "memoryType", required = false) String memoryType,
            @RequestParam(value = "character", required = false) String character,
            @RequestParam(value = "plotThread", required = false) String plotThread,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> metadataFilter = new HashMap<>();
            if (memoryType != null && !memoryType.isBlank()) {
                metadataFilter.put("memoryType", splitCsv(memoryType));
            }
            if (character != null && !character.isBlank()) {
                metadataFilter.put("characters", splitCsv(character));
            }
            if (plotThread != null && !plotThread.isBlank()) {
                metadataFilter.put("plotThreads", splitCsv(plotThread));
            }
            IRAGService.DocumentListResult listResult = ragService.listDocuments(
                    novelId != null ? novelId : "",
                    chapterId != null ? chapterId : "",
                    page,
                    size,
                    metadataFilter);
            result.put("success", true);
            result.put("data", listResult.getList());
            result.put("total", listResult.getTotal());
        } catch (Exception e) {
            log.error("RAG 文档列表失败, novelId={}, chapterId={}", novelId, chapterId, e);
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 语义检索（可选按 novelId 过滤结果）
     * GET /api/rag/search?q=xxx&novelId=xxx&topK=10
     */
    @GetMapping("/search")
    public Map<String, Object> search(
            @RequestParam("q") String q,
            @RequestParam(value = "novelId", required = false) String novelId,
            @RequestParam(value = "topK", defaultValue = "3") int topK,
            @RequestParam(value = "memoryTypes", required = false) String memoryTypes,
            @RequestParam(value = "characters", required = false) String characters,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "plotThread", required = false) String plotThread,
            @RequestParam(value = "chapterFrom", required = false) Integer chapterFrom,
            @RequestParam(value = "chapterTo", required = false) Integer chapterTo,
            @RequestParam(value = "explain", defaultValue = "false") boolean explain) {
        Map<String, Object> result = new HashMap<>();
        try {
            StoryRetrievalQuery retrievalQuery = StoryRetrievalQuery.builder()
                    .novelId(novelId)
                    .queryText(q)
                    .memoryTypes(splitCsv(memoryTypes))
                    .characters(splitCsv(characters))
                    .locations(splitCsv(location))
                    .plotThreads(splitCsv(plotThread))
                    .chapterFrom(chapterFrom)
                    .chapterTo(chapterTo)
                    .topK(topK)
                    .explain(explain)
                    .build();
            List<IRAGService.SearchResult> list = ragService.searchStoryMemories(retrievalQuery);
            result.put("success", true);
            result.put("data", list);
            result.put("query", retrievalQuery);
        } catch (Exception e) {
            log.error("RAG 检索失败, q={}, novelId={}", q, novelId, e);
            result.put("success", false);
            result.put("message", "检索失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 按文档 ID 删除
     * DELETE /api/rag/document?id=xxx
     */
    @GetMapping("/document")
    public Map<String, Object> getDocumentById(@RequestParam("id") String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            IRAGService.DocumentItem item = ragService.getDocumentById(id);
            result.put("success", true);
            result.put("data", item);
            result.put("message", item != null ? "查询成功" : "文档不存在");
        } catch (Exception e) {
            log.error("RAG 查询文档详情失败, id={}", id, e);
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/document")
    public Map<String, Object> deleteById(@RequestParam("id") String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            ragService.deleteById(id);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("RAG 删除文档失败, id={}", id, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 按小说 ID 删除该小说下所有 RAG 文档
     * DELETE /api/rag/documents?novelId=xxx
     */
    @DeleteMapping("/documents")
    public Map<String, Object> deleteByNovelId(@RequestParam("novelId") String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ragService.deleteByNovelId(novelId);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("RAG 按小说删除失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    private List<String> splitCsv(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return list;
        }
        for (String part : raw.split(",")) {
            if (part != null && !part.trim().isEmpty()) {
                list.add(part.trim());
            }
        }
        return list;
    }
}
