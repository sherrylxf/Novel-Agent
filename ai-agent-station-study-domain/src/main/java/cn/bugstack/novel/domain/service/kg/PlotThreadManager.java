package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.model.entity.Scene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 剧情线状态机。
 * 根据抽取信号推进/开启/回收 PlotThread。
 */
@Slf4j
@Service
public class PlotThreadManager {

    @Autowired(required = false)
    private IKnowledgeGraphService knowledgeGraphService;

    public void applySignals(String novelId, ExtractedEntities entities, ChapterOutline outline, Scene scene) {
        if (knowledgeGraphService == null || entities == null || novelId == null || novelId.isBlank()) {
            return;
        }
        List<ExtractedEntities.PlotThreadSignal> signals = entities.getPlotThreadSignals();
        if (signals == null || signals.isEmpty()) {
            return;
        }
        for (ExtractedEntities.PlotThreadSignal signal : signals) {
            if (signal == null || !KgStorySyncUtil.hasMeaningfulText(signal.getThreadTitle())) {
                continue;
            }
            String threadTitle = signal.getThreadTitle().trim();
            String threadId = KgStorySyncUtil.toPlotThreadId(novelId, threadTitle);
            String status = mapSignalToStatus(signal.getSignalType());
            Map<String, Object> props = new HashMap<>();
            props.put("novelId", novelId);
            props.put("chapterId", outline != null ? outline.getChapterId() : null);
            props.put("chapterNumber", outline != null ? outline.getChapterNumber() : null);
            props.put("sceneId", scene != null ? scene.getSceneId() : null);
            props.put("summary", !safe(signal.getSummary()).isEmpty() ? signal.getSummary() : signal.getEvidence());
            props.put("evidence", safe(signal.getEvidence()));
            props.put("signalType", safe(signal.getSignalType()));
            props.put("sourceType", "SCENE_EXTRACTION");
            props.put("lastAdvancedChapter", outline != null ? outline.getChapterNumber() : null);
            knowledgeGraphService.upsertPlotThread(novelId, threadId, threadTitle, status, props);

            String relatedEventName = safe(signal.getRelatedEvent());
            if (!relatedEventName.isEmpty()) {
                String eventId = KgStorySyncUtil.toSceneEventId(novelId,
                        outline != null ? outline.getChapterId() : null,
                        scene != null ? scene.getSceneId() : null,
                        relatedEventName);
                knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Event", eventId, "ADVANCED_BY", props);
            }
            if ("RESOLVED".equals(status)) {
                log.info("剧情线已回收: novelId={}, threadTitle={}", novelId, threadTitle);
            }
        }
    }

    private static String mapSignalToStatus(String signalType) {
        String normalized = safe(signalType).toUpperCase();
        if ("RESOLVE".equals(normalized) || "RESOLVED".equals(normalized) || "CLOSE".equals(normalized)) {
            return "RESOLVED";
        }
        if ("ABANDON".equals(normalized) || "ABANDONED".equals(normalized)) {
            return "ABANDONED";
        }
        if ("OPEN".equals(normalized) || "START".equals(normalized)) {
            return "ACTIVE";
        }
        return "ACTIVE";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
