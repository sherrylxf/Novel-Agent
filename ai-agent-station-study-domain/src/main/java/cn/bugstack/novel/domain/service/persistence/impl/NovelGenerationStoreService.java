package cn.bugstack.novel.domain.service.persistence.impl;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelGenerationRepository;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.export.IChapterFileStore;
import cn.bugstack.novel.domain.service.persistence.INovelGenerationStoreService;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import cn.bugstack.novel.domain.model.entity.NovelProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Slf4j
@Service
public class NovelGenerationStoreService implements INovelGenerationStoreService {

    @Resource
    private INovelGenerationRepository repository;

    @Autowired(required = false)
    private IChapterFileStore chapterFileStore;

    @Resource
    private INovelWorkspaceService novelWorkspaceService;

    @Override
    public void persistSeed(String novelId, NovelSeed seed) {
        if (seed == null) return;
        repository.saveOrUpdateSeed(novelId, seed);
        novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                .novelId(novelId)
                .title(seed.getTitle())
                .genre(seed.getGenre() != null ? seed.getGenre().name() : null)
                .status(1)
                .build());
    }

    @Override
    public void persistPlan(NovelPlan plan) {
        if (plan == null) return;
        repository.saveOrUpdatePlan(plan);
        novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                .novelId(plan.getNovelId())
                .status(1)
                .build());
    }

    @Override
    public void persistVolumePlan(String novelId, VolumePlan volumePlan) {
        if (volumePlan == null) return;
        repository.saveOrUpdateVolumePlan(novelId, volumePlan);
        novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                .novelId(volumePlan.getNovelId() != null ? volumePlan.getNovelId() : novelId)
                .status(1)
                .build());
    }

    @Override
    public String persistChapterAndScene(String novelId, Integer volumeNumber, ChapterOutline outline, Scene scene) {
        if (outline == null || scene == null) {
            return null;
        }

        String chapterId = repository.saveOrUpdateChapter(novelId, volumeNumber, outline, scene);
        repository.saveOrUpdateScene(chapterId, scene);

        if (chapterFileStore != null) {
            try {
                chapterFileStore.writeChapterTxt(
                        novelId,
                        volumeNumber,
                        outline.getChapterNumber(),
                        outline.getChapterTitle(),
                        scene.getContent()
                );
            } catch (Exception e) {
                // 文件导出失败不应阻塞主流程
                log.warn("导出章节txt失败，novelId: {}, volumeNumber: {}, chapterNumber: {}, err: {}",
                        novelId, volumeNumber, outline.getChapterNumber(), e.getMessage());
            }
        }

        novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                .novelId(novelId)
                .status(1)
                .build());

        return chapterId;
    }
}

