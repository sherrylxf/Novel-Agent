package cn.bugstack.novel.infrastructure.adapter.repository;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelGenerationRepository;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.infrastructure.dao.IChapterDao;
import cn.bugstack.novel.infrastructure.dao.INovelPlanDao;
import cn.bugstack.novel.infrastructure.dao.INovelSeedDao;
import cn.bugstack.novel.infrastructure.dao.ISceneDao;
import cn.bugstack.novel.infrastructure.dao.IVolumePlanDao;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 生成过程数据落库仓储实现
 */
@Slf4j
@Repository
public class NovelGenerationRepository implements INovelGenerationRepository {

    private final INovelSeedDao novelSeedDao;
    private final INovelPlanDao novelPlanDao;
    private final IVolumePlanDao volumePlanDao;
    private final IChapterDao chapterDao;
    private final ISceneDao sceneDao;

    public NovelGenerationRepository(
            INovelSeedDao novelSeedDao,
            INovelPlanDao novelPlanDao,
            IVolumePlanDao volumePlanDao,
            IChapterDao chapterDao,
            ISceneDao sceneDao
    ) {
        this.novelSeedDao = novelSeedDao;
        this.novelPlanDao = novelPlanDao;
        this.volumePlanDao = volumePlanDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void saveOrUpdateSeed(String novelId, NovelSeed seed) {
        if (seed == null || seed.getSeedId() == null) return;

        var existing = novelSeedDao.queryBySeedId(seed.getSeedId());
        cn.bugstack.novel.infrastructure.dao.po.NovelSeed po = cn.bugstack.novel.infrastructure.dao.po.NovelSeed.builder()
                .seedId(seed.getSeedId())
                .novelId(novelId)
                .title(seed.getTitle())
                .genre(seed.getGenre() != null ? seed.getGenre().name() : null)
                .coreConflict(seed.getCoreConflict())
                .worldSetting(seed.getWorldSetting())
                .protagonistSetting(seed.getProtagonistSetting())
                .targetWordCount(seed.getTargetWordCount())
                .build();

        if (existing == null) {
            novelSeedDao.insert(po);
        } else {
            novelSeedDao.updateBySeedId(po);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void saveOrUpdatePlan(NovelPlan plan) {
        if (plan == null || plan.getPlanId() == null) return;

        var existing = novelPlanDao.queryByPlanId(plan.getPlanId());
        cn.bugstack.novel.infrastructure.dao.po.NovelPlan po = cn.bugstack.novel.infrastructure.dao.po.NovelPlan.builder()
                .planId(plan.getPlanId())
                .novelId(plan.getNovelId())
                .totalVolumes(plan.getTotalVolumes())
                .chaptersPerVolume(plan.getChaptersPerVolume())
                .overallOutline(plan.getOverallOutline())
                .build();

        if (existing == null) {
            novelPlanDao.insert(po);
        } else {
            novelPlanDao.updateByPlanId(po);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void saveOrUpdateVolumePlan(String novelId, VolumePlan volumePlan) {
        if (volumePlan == null || volumePlan.getVolumeNumber() == null) return;

        String finalNovelId = volumePlan.getNovelId() != null ? volumePlan.getNovelId() : novelId;
        var existing = volumePlanDao.queryByNovelIdAndVolumeNumber(finalNovelId, volumePlan.getVolumeNumber());

        cn.bugstack.novel.infrastructure.dao.po.VolumePlan po = cn.bugstack.novel.infrastructure.dao.po.VolumePlan.builder()
                .volumeId(existing != null ? existing.getVolumeId() : volumePlan.getVolumeId())
                .novelId(finalNovelId)
                .volumeNumber(volumePlan.getVolumeNumber())
                .volumeTitle(volumePlan.getVolumeTitle())
                .volumeTheme(volumePlan.getVolumeTheme())
                .chapterCount(volumePlan.getChapterCount())
                .build();

        if (existing == null) {
            volumePlanDao.insert(po);
        } else {
            volumePlanDao.updateByVolumeId(po);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public String saveOrUpdateChapter(String novelId, Integer volumeNumber, ChapterOutline outline, Scene scene) {
        if (outline == null) return null;
        if (novelId == null) return null;
        if (volumeNumber == null) return null;
        if (outline.getChapterNumber() == null) return null;

        cn.bugstack.novel.infrastructure.dao.po.Chapter existing =
                chapterDao.queryByNovelIdAndVolumeAndChapter(novelId, volumeNumber, outline.getChapterNumber());

        String chapterId = existing != null ? existing.getChapterId() : outline.getChapterId();
        if (chapterId == null) {
            // 兜底：避免空主键
            chapterId = java.util.UUID.randomUUID().toString();
        }

        String content = scene != null ? scene.getContent() : null;
        Integer wordCount = scene != null ? scene.getWordCount() : null;
        if (wordCount == null && content != null) {
            wordCount = content.length();
        }

        cn.bugstack.novel.infrastructure.dao.po.Chapter po = cn.bugstack.novel.infrastructure.dao.po.Chapter.builder()
                .chapterId(chapterId)
                .novelId(novelId)
                .volumeNumber(volumeNumber)
                .chapterNumber(outline.getChapterNumber())
                .title(outline.getChapterTitle())
                .outline(outline.getOutline())
                .content(content)
                .wordCount(wordCount != null ? wordCount : 0)
                .status(1)
                .build();

        if (existing == null) {
            chapterDao.insert(po);
        } else {
            chapterDao.updateByChapterId(po);
        }

        return chapterId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void saveOrUpdateScene(String chapterId, Scene scene) {
        if (chapterId == null || scene == null) return;

        Integer sceneNumber = scene.getSceneNumber() != null ? scene.getSceneNumber() : 1;
        cn.bugstack.novel.infrastructure.dao.po.Scene existing = sceneDao.queryByChapterIdAndSceneNumber(chapterId, sceneNumber);

        String charactersJson = null;
        if (scene.getCharacters() != null) {
            charactersJson = JSON.toJSONString(scene.getCharacters());
        }

        cn.bugstack.novel.infrastructure.dao.po.Scene po = cn.bugstack.novel.infrastructure.dao.po.Scene.builder()
                .sceneId(existing != null ? existing.getSceneId() : scene.getSceneId())
                .chapterId(chapterId)
                .sceneNumber(sceneNumber)
                .sceneTitle(scene.getSceneTitle())
                .sceneType(scene.getSceneType())
                .content(scene.getContent())
                .wordCount(Objects.requireNonNullElse(scene.getWordCount(), scene.getContent() != null ? scene.getContent().length() : 0))
                .characters(charactersJson)
                .location(scene.getLocation())
                .build();

        if (existing == null) {
            sceneDao.insert(po);
        } else {
            sceneDao.updateBySceneId(po);
        }
    }
}

