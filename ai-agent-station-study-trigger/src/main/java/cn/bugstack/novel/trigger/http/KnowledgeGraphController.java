package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KGGraphDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱 HTTP 接口：图数据、人物/伏笔列表与增删改
 */
@Slf4j
@RestController
@RequestMapping("/api/kg")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.PUT, RequestMethod.OPTIONS})
public class KnowledgeGraphController {

    @Resource
    private IKnowledgeGraphService knowledgeGraphService;

    /**
     * 检查图数据库（Neo4j）是否已配置且可连接
     * GET /api/kg/health
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean connected = knowledgeGraphService.isConnected();
            result.put("success", true);
            result.put("connected", connected);
            result.put("message", connected ? "Neo4j 已连接" : "Neo4j 未配置或不可达（使用占位实现）");
        } catch (Exception e) {
            log.error("图数据库健康检查失败", e);
            result.put("success", false);
            result.put("connected", false);
            result.put("message", "检查失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 获取某小说的知识图谱（节点+边），供前端图可视化
     * GET /api/kg/graph?novelId=xxx
     */
    @GetMapping("/graph")
    public Map<String, Object> getGraph(@RequestParam(required = false) String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            KGGraphDTO dto = knowledgeGraphService.getGraph(novelId != null ? novelId : "");
            result.put("success", true);
            result.put("nodes", dto.getNodes());
            result.put("edges", dto.getEdges());
        } catch (Exception e) {
            log.error("获取知识图谱失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", "获取失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 按小说列出人物
     * GET /api/kg/characters?novelId=xxx
     */
    @GetMapping("/characters")
    public Map<String, Object> listCharacters(@RequestParam(required = false) String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = knowledgeGraphService.listCharacters(novelId != null ? novelId : "");
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            log.error("列出人物失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 按小说列出伏笔
     * GET /api/kg/foreshadowing?novelId=xxx
     */
    @GetMapping("/foreshadowing")
    public Map<String, Object> listForeshadowing(@RequestParam(required = false) String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = knowledgeGraphService.listForeshadowing(novelId != null ? novelId : "");
            result.put("success", true);
            result.put("data", list);
        } catch (Exception e) {
            log.error("列出伏笔失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 创建人物
     * POST /api/kg/character
     * Body: { "characterId", "name", "type", "personality", "background", "abilities", "novelId"? }
     */
    @PostMapping("/character")
    public Map<String, Object> createCharacter(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Character c = Character.builder()
                    .characterId((String) body.get("characterId"))
                    .name((String) body.get("name"))
                    .type((String) body.get("type"))
                    .personality((String) body.get("personality"))
                    .background((String) body.get("background"))
                    .abilities((String) body.get("abilities"))
                    .novelId((String) body.get("novelId"))
                    .build();
            knowledgeGraphService.createCharacter(c);
            result.put("success", true);
            result.put("message", "创建成功");
        } catch (Exception e) {
            log.error("创建人物失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 更新人物
     * PUT /api/kg/character
     */
    @PutMapping("/character")
    public Map<String, Object> updateCharacter(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Character c = Character.builder()
                    .characterId((String) body.get("characterId"))
                    .name((String) body.get("name"))
                    .type((String) body.get("type"))
                    .personality((String) body.get("personality"))
                    .background((String) body.get("background"))
                    .abilities((String) body.get("abilities"))
                    .novelId((String) body.get("novelId"))
                    .build();
            knowledgeGraphService.updateCharacter(c);
            result.put("success", true);
            result.put("message", "更新成功");
        } catch (Exception e) {
            log.error("更新人物失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 删除人物
     * DELETE /api/kg/character?characterId=xxx
     */
    @DeleteMapping("/character")
    public Map<String, Object> deleteCharacter(@RequestParam String characterId) {
        Map<String, Object> result = new HashMap<>();
        try {
            knowledgeGraphService.deleteCharacter(characterId);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("删除人物失败, characterId={}", characterId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 创建伏笔
     * POST /api/kg/foreshadowing
     * Body: { "foreshadowingId", "content", "novelId"? }
     */
    @PostMapping("/foreshadowing")
    public Map<String, Object> createForeshadowing(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String id = (String) body.get("foreshadowingId");
            String content = (String) body.get("content");
            Map<String, Object> props = new HashMap<>();
            if (body.containsKey("novelId")) props.put("novelId", body.get("novelId"));
            if (body.containsKey("resolved")) props.put("resolved", body.get("resolved"));
            if (body.containsKey("chapterId")) props.put("chapterId", body.get("chapterId"));
            knowledgeGraphService.createForeshadowing(id, content != null ? content : "", props);
            result.put("success", true);
            result.put("message", "创建成功");
        } catch (Exception e) {
            log.error("创建伏笔失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 更新伏笔
     * PUT /api/kg/foreshadowing
     */
    @PutMapping("/foreshadowing")
    public Map<String, Object> updateForeshadowing(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String id = (String) body.get("foreshadowingId");
            String content = (String) body.get("content");
            Map<String, Object> props = new HashMap<>();
            if (body.containsKey("resolved")) props.put("resolved", body.get("resolved"));
            if (body.containsKey("novelId")) props.put("novelId", body.get("novelId"));
            knowledgeGraphService.updateForeshadowing(id, content, props);
            result.put("success", true);
            result.put("message", "更新成功");
        } catch (Exception e) {
            log.error("更新伏笔失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 删除伏笔
     * DELETE /api/kg/foreshadowing?foreshadowingId=xxx
     */
    @DeleteMapping("/foreshadowing")
    public Map<String, Object> deleteForeshadowing(@RequestParam String foreshadowingId) {
        Map<String, Object> result = new HashMap<>();
        try {
            knowledgeGraphService.deleteForeshadowing(foreshadowingId);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("删除伏笔失败, foreshadowingId={}", foreshadowingId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
