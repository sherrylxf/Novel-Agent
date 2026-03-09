package cn.bugstack.novel.domain.service.novel.impl;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelRepository;
import cn.bugstack.novel.domain.model.entity.ChapterDetail;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.model.valobj.NovelSeedVO;
import cn.bugstack.novel.domain.service.novel.INovelContinuationService;
import cn.bugstack.novel.domain.service.novel.INovelPlanService;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 基于数据库恢复续写上下文
 */
@Slf4j
@Service
public class NovelContinuationService implements INovelContinuationService {

    private final INovelRepository novelRepository;
    private final INovelPlanService novelPlanService;
    private final INovelWorkspaceService novelWorkspaceService;

    public NovelContinuationService(INovelRepository novelRepository,
                                    INovelPlanService novelPlanService,
                                    INovelWorkspaceService novelWorkspaceService) {
        this.novelRepository = novelRepository;
        this.novelPlanService = novelPlanService;
        this.novelWorkspaceService = novelWorkspaceService;
    }

    @Override
    public NovelContext buildResumeContext(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId不能为空");
        }

        NovelPlan plan = novelPlanService.queryByNovelId(novelId);
        if (plan == null) {
            throw new IllegalStateException("该小说尚未保存大纲，无法继续创作");
        }

        NovelSeedVO seed = novelRepository.queryNovelSeedByNovelId(novelId);
        List<ChapterDetail> chapters = novelWorkspaceService.queryChapters(novelId).stream()
                .sorted(Comparator.comparing(ChapterDetail::getVolumeNumber, Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(ChapterDetail::getChapterNumber, Comparator.nullsFirst(Integer::compareTo)))
                .toList();

        ResumePointer pointer = resolveResumePointer(plan, chapters);

        NovelContext context = NovelContext.builder()
                .novelId(novelId)
                .currentStage(pointer.stage)
                .build();

        context.setAttribute("plan", plan);
        context.setAttribute("currentVolumeNumber", pointer.volumeNumber);
        context.setAttribute("currentChapterInVolume", pointer.chapterNumber);
        if (pointer.currentVolume != null) {
            context.setAttribute("currentVolume", pointer.currentVolume);
        }

        if (seed != null) {
            context.setAttribute("genre", seed.getGenre());
            context.setAttribute("coreConflict", seed.getCoreConflict());
            context.setAttribute("worldSetting", seed.getWorldSetting());
        }

        log.info("恢复续写上下文成功，novelId={}, stage={}, volume={}, chapter={}",
                novelId, pointer.stage, pointer.volumeNumber, pointer.chapterNumber);
        return context;
    }

    private ResumePointer resolveResumePointer(NovelPlan plan, List<ChapterDetail> chapters) {
        List<VolumePlan> volumePlans = plan.getVolumePlans();
        int totalVolumes = plan.getTotalVolumes() != null ? plan.getTotalVolumes() : Math.max(volumePlans != null ? volumePlans.size() : 0, 1);

        if (chapters.isEmpty()) {
            VolumePlan firstVolume = findVolumePlan(volumePlans, 1);
            if (firstVolume != null) {
                return new ResumePointer(GenerationStage.CHAPTER_OUTLINE.name(), 1, 1, firstVolume);
            }
            return new ResumePointer(GenerationStage.VOLUME_PLAN.name(), 1, 1, null);
        }

        ChapterDetail latestChapter = chapters.get(chapters.size() - 1);
        Integer currentVolumeNumber = latestChapter.getVolumeNumber() != null ? latestChapter.getVolumeNumber() : 1;
        Integer latestChapterNumber = latestChapter.getChapterNumber() != null ? latestChapter.getChapterNumber() : 0;
        VolumePlan currentVolume = findVolumePlan(volumePlans, currentVolumeNumber);
        int chapterCount = currentVolume != null && currentVolume.getChapterCount() != null
                ? currentVolume.getChapterCount()
                : plan.getChaptersPerVolume() != null ? plan.getChaptersPerVolume() : 20;

        if (latestChapterNumber < chapterCount) {
            if (currentVolume == null) {
                currentVolume = buildFallbackVolume(plan, currentVolumeNumber, chapterCount);
            }
            return new ResumePointer(GenerationStage.CHAPTER_OUTLINE.name(), currentVolumeNumber, latestChapterNumber + 1, currentVolume);
        }

        if (currentVolumeNumber < totalVolumes) {
            int nextVolumeNumber = currentVolumeNumber + 1;
            VolumePlan nextVolume = findVolumePlan(volumePlans, nextVolumeNumber);
            if (nextVolume != null) {
                return new ResumePointer(GenerationStage.CHAPTER_OUTLINE.name(), nextVolumeNumber, 1, nextVolume);
            }
            return new ResumePointer(GenerationStage.VOLUME_PLAN.name(), nextVolumeNumber, 1, null);
        }

        return new ResumePointer(GenerationStage.COMPLETE.name(), currentVolumeNumber, latestChapterNumber, currentVolume);
    }

    private VolumePlan findVolumePlan(List<VolumePlan> volumePlans, Integer volumeNumber) {
        if (volumePlans == null || volumePlans.isEmpty() || volumeNumber == null) {
            return null;
        }
        return volumePlans.stream()
                .filter(item -> item != null && volumeNumber.equals(item.getVolumeNumber()))
                .findFirst()
                .orElse(null);
    }

    private VolumePlan buildFallbackVolume(NovelPlan plan, Integer volumeNumber, Integer chapterCount) {
        return VolumePlan.builder()
                .novelId(plan.getNovelId())
                .volumeNumber(volumeNumber)
                .volumeTitle("第" + volumeNumber + "卷")
                .volumeTheme(plan.getOverallOutline())
                .chapterCount(chapterCount)
                .build();
    }

    private record ResumePointer(String stage, Integer volumeNumber, Integer chapterNumber, VolumePlan currentVolume) {
    }
}
