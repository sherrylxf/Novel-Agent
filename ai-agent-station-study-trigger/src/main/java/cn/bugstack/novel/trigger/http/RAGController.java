package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.domain.service.rag.IRAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            @RequestParam(required = false) String novelId,
            @RequestParam(required = false) String chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            IRAGService.DocumentListResult listResult = ragService.listDocuments(
                    novelId != null ? novelId : "",
                    chapterId != null ? chapterId : "",
                    page,
                    size);
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
            @RequestParam String q,
            @RequestParam(required = false) String novelId,
            @RequestParam(defaultValue = "10") int topK) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<IRAGService.SearchResult> list = ragService.search(q, "zh", topK);
            if (novelId != null && !novelId.isEmpty()) {
                list = list.stream()
                        .filter(sr -> sr.getMetadata() != null && novelId.equals(sr.getMetadata().get("novelId")))
                        .collect(Collectors.toList());
            }
            result.put("success", true);
            result.put("data", list);
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
    @DeleteMapping("/document")
    public Map<String, Object> deleteById(@RequestParam String id) {
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
    public Map<String, Object> deleteByNovelId(@RequestParam String novelId) {
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
}
