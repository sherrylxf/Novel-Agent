package cn.bugstack.novel.domain.service.plot.impl;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.StoryProgress;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.plot.IPlotTrackerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Plot Tracker 实现：组合 MySQL 规划/章节与 Neo4j 伏笔，构建 StoryProgress
 */
@Slf4j
@Service
public class PlotTrackerServiceImpl implements IPlotTrackerService {

    @Resource
    private IKnowledgeGraphService knowledgeGraphService;

    @Override
    public StoryProgress buildProgress(NovelContext context, String storySummary) {
        if (context == null) {
            return StoryProgress.builder()
                    .storySummary(storySummary != null ? storySummary : "")
                    .currentChapterNumber(1)
                    .plannedTotalChapters(null)
                    .unresolvedForeshadowings(new ArrayList<>())
                    .activePlotThreads(new ArrayList<>())
                    .build();
        }
        NovelPlan plan = context.getAttribute("plan");
        Integer currentVolumeNumber = context.getAttribute("currentVolumeNumber");
        Integer currentChapterInVolume = context.getAttribute("currentChapterInVolume");
        if (currentVolumeNumber == null) currentVolumeNumber = 1;
        if (currentChapterInVolume == null) currentChapterInVolume = 1;

        int plannedTotalChapters = 0;
        int currentChapterNumber = 1;
        if (plan != null) {
            int pv = plan.getTotalVolumes() != null ? plan.getTotalVolumes() : 1;
            int cv = plan.getChaptersPerVolume() != null ? plan.getChaptersPerVolume() : 20;
            plannedTotalChapters = pv * cv;
            currentChapterNumber = (currentVolumeNumber - 1) * cv + currentChapterInVolume;
        }

        List<String> fromOutlines = context.getAttribute("unresolvedForeshadowingsFromChapters");
        List<String> unresolved = collectUnresolvedForeshadowing(context.getNovelId(), fromOutlines);
        List<String> activePlotThreads = knowledgeGraphService.listActivePlotThreads(context.getNovelId(), 8);

        log.info("Plot Tracker: 当前进度 第{}章/共{}章, 未解决伏笔数={}, 活跃剧情线程数={}",
                currentChapterNumber, plannedTotalChapters, unresolved.size(), activePlotThreads.size());
        return StoryProgress.builder()
                .storySummary(storySummary != null ? storySummary : "")
                .currentChapterNumber(currentChapterNumber)
                .plannedTotalChapters(plannedTotalChapters > 0 ? plannedTotalChapters : null)
                .unresolvedForeshadowings(unresolved)
                .activePlotThreads(activePlotThreads)
                .build();
    }

    @Override
    public List<String> collectUnresolvedForeshadowing(String novelId, List<String> fromChapterOutlines) {
        List<String> fromKg = knowledgeGraphService.listUnresolvedForeshadowing(novelId);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (fromKg != null) set.addAll(fromKg);
        if (fromChapterOutlines != null) set.addAll(fromChapterOutlines);
        return new ArrayList<>(set);
    }
}
