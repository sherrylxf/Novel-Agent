package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.domain.model.entity.ChapterDetail;
import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import cn.bugstack.novel.domain.model.entity.NovelProject;
import cn.bugstack.novel.domain.model.entity.SceneDetail;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小说工作台管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workspace")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class NovelWorkspaceController {

    @Resource
    private INovelWorkspaceService novelWorkspaceService;

    @GetMapping("/novels")
    public Map<String, Object> queryNovels() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("success", true);
            result.put("data", novelWorkspaceService.queryNovelProjects().stream()
                    .map(this::toNovelDTO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询小说列表失败", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/novels/{novelId}")
    public Map<String, Object> queryNovel(@PathVariable("novelId") String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            NovelProject project = novelWorkspaceService.queryNovelProject(novelId);
            if (project == null) {
                result.put("success", false);
                result.put("message", "小说不存在");
                return result;
            }
            result.put("success", true);
            result.put("data", toNovelDTO(project));
        } catch (Exception e) {
            log.error("查询小说详情失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/novels/save")
    public Map<String, Object> saveNovel(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            NovelProject saved = novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                    .novelId(asString(body.get("novelId")))
                    .title(asString(body.get("title")))
                    .genre(asString(body.get("genre")))
                    .status(asInteger(body.get("status")))
                    .build());
            result.put("success", true);
            result.put("data", toNovelDTO(saved));
        } catch (Exception e) {
            log.error("保存小说失败, body={}", body, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/novels/archive")
    public Map<String, Object> archiveNovel(@RequestParam("novelId") String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            novelWorkspaceService.archiveNovel(novelId);
            result.put("success", true);
            result.put("message", "归档成功");
        } catch (Exception e) {
            log.error("归档小说失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/configs")
    public Map<String, Object> queryConfigs(@RequestParam(value = "novelId", required = false) String novelId,
                                            @RequestParam(value = "includeGlobal", defaultValue = "true") boolean includeGlobal) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<NovelAgentConfigItem> items = novelWorkspaceService.queryConfigs(novelId, includeGlobal);
            result.put("success", true);
            result.put("data", items.stream().map(this::toConfigDTO).collect(Collectors.toList()));
            result.put("globalConfigs", items.stream()
                    .filter(item -> item.getNovelId() == null || item.getNovelId().isEmpty())
                    .map(this::toConfigDTO)
                    .collect(Collectors.toList()));
            result.put("novelConfigs", items.stream()
                    .filter(item -> item.getNovelId() != null && !item.getNovelId().isEmpty())
                    .map(this::toConfigDTO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询配置失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/configs/save")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            NovelAgentConfigItem saved = novelWorkspaceService.saveOrUpdateConfig(NovelAgentConfigItem.builder()
                    .configId(asString(body.get("configId")))
                    .novelId(asString(body.get("novelId")))
                    .agentType(asString(body.get("agentType")))
                    .configKey(asString(body.get("configKey")))
                    .configValue(asString(body.get("configValue")))
                    .status(asInteger(body.get("status")))
                    .build());
            result.put("success", true);
            result.put("data", toConfigDTO(saved));
        } catch (Exception e) {
            log.error("保存配置失败, body={}", body, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/configs/{configId}")
    public Map<String, Object> deleteConfig(@PathVariable("configId") String configId) {
        Map<String, Object> result = new HashMap<>();
        try {
            novelWorkspaceService.deleteConfig(configId);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("删除配置失败, configId={}", configId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/chapters")
    public Map<String, Object> queryChapters(@RequestParam("novelId") String novelId) {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("success", true);
            result.put("data", novelWorkspaceService.queryChapters(novelId).stream()
                    .map(this::toChapterDTO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询章节列表失败, novelId={}", novelId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/chapters/{chapterId}")
    public Map<String, Object> queryChapterDetail(@PathVariable("chapterId") String chapterId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ChapterDetail detail = novelWorkspaceService.queryChapterDetail(chapterId);
            if (detail == null) {
                result.put("success", false);
                result.put("message", "章节不存在");
                return result;
            }
            result.put("success", true);
            result.put("data", toChapterDTO(detail));
        } catch (Exception e) {
            log.error("查询章节详情失败, chapterId={}", chapterId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/chapters/save")
    public Map<String, Object> saveChapter(@RequestBody Map<String, Object> body) {
        return saveOrUpdateChapter(body);
    }

    @PostMapping("/chapters/update")
    public Map<String, Object> updateChapter(@RequestBody Map<String, Object> body) {
        return saveOrUpdateChapter(body);
    }

    @DeleteMapping("/chapters/{chapterId}")
    public Map<String, Object> deleteChapter(@PathVariable("chapterId") String chapterId) {
        Map<String, Object> result = new HashMap<>();
        try {
            novelWorkspaceService.deleteChapter(chapterId);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            log.error("删除章节失败, chapterId={}", chapterId, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> saveOrUpdateChapter(Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            ChapterDetail saved = novelWorkspaceService.saveOrUpdateChapter(ChapterDetail.builder()
                    .chapterId(asString(body.get("chapterId")))
                    .novelId(asString(body.get("novelId")))
                    .volumeNumber(asInteger(body.get("volumeNumber")))
                    .chapterNumber(asInteger(body.get("chapterNumber")))
                    .title(asString(body.get("title")))
                    .outline(asString(body.get("outline")))
                    .content(asString(body.get("content")))
                    .wordCount(asInteger(body.get("wordCount")))
                    .status(asInteger(body.get("status")))
                    .scenes(toSceneDetails(body.get("scenes")))
                    .build());
            result.put("success", true);
            result.put("data", toChapterDTO(saved));
        } catch (Exception e) {
            log.error("保存章节失败, body={}", body, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<SceneDetail> toSceneDetails(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }

        List<SceneDetail> sceneDetails = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            sceneDetails.add(SceneDetail.builder()
                    .sceneId(asString(map.get("sceneId")))
                    .sceneNumber(asInteger(map.get("sceneNumber")))
                    .sceneTitle(asString(map.get("sceneTitle")))
                    .sceneType(asString(map.get("sceneType")))
                    .content(asString(map.get("content")))
                    .wordCount(asInteger(map.get("wordCount")))
                    .characters(asString(map.get("characters")))
                    .location(asString(map.get("location")))
                    .build());
        }
        return sceneDetails;
    }

    private Map<String, Object> toNovelDTO(NovelProject project) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", project.getId());
        dto.put("novelId", project.getNovelId());
        dto.put("title", project.getTitle());
        dto.put("genre", project.getGenre());
        dto.put("status", project.getStatus());
        dto.put("coreConflict", project.getCoreConflict());
        dto.put("worldSetting", project.getWorldSetting());
        dto.put("latestPlanId", project.getLatestPlanId());
        dto.put("totalVolumes", project.getTotalVolumes());
        dto.put("chaptersPerVolume", project.getChaptersPerVolume());
        dto.put("chapterCount", project.getChapterCount());
        dto.put("hasPlan", project.getHasPlan());
        dto.put("createTime", project.getCreateTime());
        dto.put("updateTime", project.getUpdateTime());
        return dto;
    }

    private Map<String, Object> toConfigDTO(NovelAgentConfigItem item) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", item.getId());
        dto.put("configId", item.getConfigId());
        dto.put("novelId", item.getNovelId());
        dto.put("agentType", item.getAgentType());
        dto.put("configKey", item.getConfigKey());
        dto.put("configValue", item.getConfigValue());
        dto.put("status", item.getStatus());
        dto.put("createTime", item.getCreateTime());
        dto.put("updateTime", item.getUpdateTime());
        return dto;
    }

    private Map<String, Object> toChapterDTO(ChapterDetail detail) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("chapterId", detail.getChapterId());
        dto.put("novelId", detail.getNovelId());
        dto.put("volumeNumber", detail.getVolumeNumber());
        dto.put("chapterNumber", detail.getChapterNumber());
        dto.put("title", detail.getTitle());
        dto.put("outline", detail.getOutline());
        dto.put("content", detail.getContent());
        dto.put("wordCount", detail.getWordCount());
        dto.put("status", detail.getStatus());
        dto.put("createTime", detail.getCreateTime());
        dto.put("updateTime", detail.getUpdateTime());
        dto.put("scenes", detail.getScenes() == null ? List.of() : detail.getScenes().stream()
                .map(this::toSceneDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private Map<String, Object> toSceneDTO(SceneDetail detail) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("sceneId", detail.getSceneId());
        dto.put("sceneNumber", detail.getSceneNumber());
        dto.put("sceneTitle", detail.getSceneTitle());
        dto.put("sceneType", detail.getSceneType());
        dto.put("content", detail.getContent());
        dto.put("wordCount", detail.getWordCount());
        dto.put("characters", detail.getCharacters());
        dto.put("location", detail.getLocation());
        return dto;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
