package cn.bugstack.novel.infrastructure.adapter.repository;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelWorkspaceRepository;
import cn.bugstack.novel.domain.model.entity.ChapterDetail;
import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import cn.bugstack.novel.domain.model.entity.NovelProject;
import cn.bugstack.novel.domain.model.entity.SceneDetail;
import cn.bugstack.novel.infrastructure.dao.IChapterDao;
import cn.bugstack.novel.infrastructure.dao.INovelAgentConfigDao;
import cn.bugstack.novel.infrastructure.dao.INovelDao;
import cn.bugstack.novel.infrastructure.dao.INovelPlanDao;
import cn.bugstack.novel.infrastructure.dao.INovelSeedDao;
import cn.bugstack.novel.infrastructure.dao.ISceneDao;
import cn.bugstack.novel.infrastructure.dao.po.Chapter;
import cn.bugstack.novel.infrastructure.dao.po.Novel;
import cn.bugstack.novel.infrastructure.dao.po.NovelAgentConfig;
import cn.bugstack.novel.infrastructure.dao.po.NovelPlan;
import cn.bugstack.novel.infrastructure.dao.po.NovelSeed;
import cn.bugstack.novel.infrastructure.dao.po.Scene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 小说工作台仓储实现
 */
@Slf4j
@Repository
public class NovelWorkspaceRepository implements INovelWorkspaceRepository {

    private final INovelDao novelDao;
    private final INovelSeedDao novelSeedDao;
    private final INovelPlanDao novelPlanDao;
    private final IChapterDao chapterDao;
    private final ISceneDao sceneDao;
    private final INovelAgentConfigDao novelAgentConfigDao;

    public NovelWorkspaceRepository(INovelDao novelDao,
                                    INovelSeedDao novelSeedDao,
                                    INovelPlanDao novelPlanDao,
                                    IChapterDao chapterDao,
                                    ISceneDao sceneDao,
                                    INovelAgentConfigDao novelAgentConfigDao) {
        this.novelDao = novelDao;
        this.novelSeedDao = novelSeedDao;
        this.novelPlanDao = novelPlanDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
        this.novelAgentConfigDao = novelAgentConfigDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public NovelProject saveOrUpdateNovel(NovelProject novelProject) {
        if (novelProject == null) {
            throw new IllegalArgumentException("novelProject不能为空");
        }

        String novelId = hasText(novelProject.getNovelId()) ? novelProject.getNovelId().trim() : "novel-" + System.currentTimeMillis();
        Novel existing = novelDao.queryByNovelId(novelId);
        NovelSeed latestSeed = queryLatestSeed(novelId);

        Novel po = Novel.builder()
                .novelId(novelId)
                .title(firstNonBlank(novelProject.getTitle(), existing != null ? existing.getTitle() : null,
                        latestSeed != null ? latestSeed.getTitle() : null, "未命名小说"))
                .genre(firstNonBlank(novelProject.getGenre(), existing != null ? existing.getGenre() : null,
                        latestSeed != null ? latestSeed.getGenre() : null))
                .status(novelProject.getStatus() != null ? novelProject.getStatus() : existing != null ? existing.getStatus() : 1)
                .build();

        if (existing == null) {
            novelDao.insert(po);
        } else {
            novelDao.updateByNovelId(po);
        }

        return queryNovelProject(novelId);
    }

    @Override
    public List<NovelProject> queryNovelProjects() {
        return novelDao.queryEnabledNovels().stream()
                .map(this::buildNovelProject)
                .collect(Collectors.toList());
    }

    @Override
    public NovelProject queryNovelProject(String novelId) {
        if (!hasText(novelId)) {
            return null;
        }
        Novel novel = novelDao.queryByNovelId(novelId.trim());
        if (novel == null) {
            return null;
        }
        return buildNovelProject(novel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void archiveNovel(String novelId) {
        Novel existing = novelDao.queryByNovelId(novelId);
        if (existing == null) {
            return;
        }

        existing.setStatus(0);
        novelDao.updateByNovelId(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public NovelAgentConfigItem saveOrUpdateConfig(NovelAgentConfigItem configItem) {
        if (configItem == null || !hasText(configItem.getAgentType()) || !hasText(configItem.getConfigKey())) {
            throw new IllegalArgumentException("配置参数不完整");
        }

        String normalizedNovelId = hasText(configItem.getNovelId()) ? configItem.getNovelId().trim() : null;
        NovelAgentConfig existing = hasText(configItem.getConfigId())
                ? novelAgentConfigDao.queryByConfigId(configItem.getConfigId())
                : novelAgentConfigDao.queryByScopeAndKey(normalizedNovelId, configItem.getAgentType(), configItem.getConfigKey());

        NovelAgentConfig po = NovelAgentConfig.builder()
                .configId(existing != null ? existing.getConfigId() : hasText(configItem.getConfigId()) ? configItem.getConfigId() : "cfg-" + UUID.randomUUID())
                .novelId(normalizedNovelId)
                .agentType(configItem.getAgentType().trim())
                .configKey(configItem.getConfigKey().trim())
                .configValue(configItem.getConfigValue())
                .status(configItem.getStatus() != null ? configItem.getStatus() : 1)
                .build();

        if (existing == null) {
            novelAgentConfigDao.insert(po);
        } else {
            novelAgentConfigDao.updateByConfigId(po);
        }

        return convertConfig(novelAgentConfigDao.queryByConfigId(po.getConfigId()));
    }

    @Override
    public List<NovelAgentConfigItem> queryConfigs(String novelId, boolean includeGlobal) {
        List<NovelAgentConfigItem> items = new ArrayList<>();
        if (includeGlobal) {
            items.addAll(novelAgentConfigDao.queryGlobalConfigs().stream()
                    .map(this::convertConfig)
                    .collect(Collectors.toList()));
        }
        if (hasText(novelId)) {
            items.addAll(novelAgentConfigDao.queryByNovelId(novelId.trim()).stream()
                    .map(this::convertConfig)
                    .collect(Collectors.toList()));
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void deleteConfig(String configId) {
        novelAgentConfigDao.deleteByConfigId(configId);
    }

    @Override
    public List<ChapterDetail> queryChapters(String novelId) {
        if (!hasText(novelId)) {
            return List.of();
        }
        return chapterDao.queryByNovelId(novelId.trim()).stream()
                .map(this::convertChapter)
                .collect(Collectors.toList());
    }

    @Override
    public ChapterDetail queryChapterDetail(String chapterId) {
        if (!hasText(chapterId)) {
            return null;
        }

        Chapter chapter = chapterDao.queryByChapterId(chapterId.trim());
        if (chapter == null) {
            return null;
        }

        ChapterDetail detail = convertChapter(chapter);
        detail.setScenes(sceneDao.queryByChapterId(chapter.getChapterId()).stream()
                .map(this::convertScene)
                .collect(Collectors.toList()));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public ChapterDetail saveOrUpdateChapter(ChapterDetail chapterDetail) {
        if (chapterDetail == null || !hasText(chapterDetail.getNovelId())) {
            throw new IllegalArgumentException("小说ID不能为空");
        }
        if (chapterDetail.getVolumeNumber() == null || chapterDetail.getChapterNumber() == null) {
            throw new IllegalArgumentException("卷号和章节号不能为空");
        }

        Chapter existing = hasText(chapterDetail.getChapterId())
                ? chapterDao.queryByChapterId(chapterDetail.getChapterId())
                : chapterDao.queryByNovelIdAndVolumeAndChapter(
                        chapterDetail.getNovelId().trim(),
                        chapterDetail.getVolumeNumber(),
                        chapterDetail.getChapterNumber()
                );

        String chapterId = existing != null ? existing.getChapterId() :
                hasText(chapterDetail.getChapterId()) ? chapterDetail.getChapterId() : UUID.randomUUID().toString();
        String content = hasText(chapterDetail.getContent()) ? chapterDetail.getContent() : mergeSceneContent(chapterDetail.getScenes());
        Integer wordCount = chapterDetail.getWordCount() != null ? chapterDetail.getWordCount() : content != null ? content.length() : 0;

        Chapter po = Chapter.builder()
                .chapterId(chapterId)
                .novelId(chapterDetail.getNovelId().trim())
                .volumeNumber(chapterDetail.getVolumeNumber())
                .chapterNumber(chapterDetail.getChapterNumber())
                .title(chapterDetail.getTitle())
                .outline(chapterDetail.getOutline())
                .content(content)
                .wordCount(wordCount)
                .status(chapterDetail.getStatus() != null ? chapterDetail.getStatus() : 1)
                .build();

        if (existing == null) {
            chapterDao.insert(po);
        } else {
            chapterDao.updateByChapterId(po);
        }

        sceneDao.deleteByChapterId(chapterId);
        List<SceneDetail> scenes = normalizeScenes(chapterDetail, chapterId, content);
        for (SceneDetail sceneDetail : scenes) {
            sceneDao.insert(Scene.builder()
                    .sceneId(hasText(sceneDetail.getSceneId()) ? sceneDetail.getSceneId() : UUID.randomUUID().toString())
                    .chapterId(chapterId)
                    .sceneNumber(sceneDetail.getSceneNumber())
                    .sceneTitle(sceneDetail.getSceneTitle())
                    .sceneType(sceneDetail.getSceneType())
                    .content(sceneDetail.getContent())
                    .wordCount(sceneDetail.getWordCount() != null ? sceneDetail.getWordCount()
                            : sceneDetail.getContent() != null ? sceneDetail.getContent().length() : 0)
                    .characters(sceneDetail.getCharacters())
                    .location(sceneDetail.getLocation())
                    .build());
        }

        saveOrUpdateNovel(NovelProject.builder()
                .novelId(chapterDetail.getNovelId())
                .status(1)
                .build());

        return queryChapterDetail(chapterId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void deleteChapter(String chapterId) {
        if (!hasText(chapterId)) {
            return;
        }
        sceneDao.deleteByChapterId(chapterId.trim());
        chapterDao.deleteByChapterId(chapterId.trim());
    }

    private NovelProject buildNovelProject(Novel novel) {
        String novelId = novel.getNovelId();
        NovelSeed latestSeed = queryLatestSeed(novelId);
        NovelPlan latestPlan = queryLatestPlan(novelId);
        int chapterCount = chapterDao.queryByNovelId(novelId).size();

        return NovelProject.builder()
                .id(novel.getId())
                .novelId(novelId)
                .title(firstNonBlank(novel.getTitle(), latestSeed != null ? latestSeed.getTitle() : null, novelId))
                .genre(firstNonBlank(novel.getGenre(), latestSeed != null ? latestSeed.getGenre() : null))
                .status(novel.getStatus())
                .coreConflict(latestSeed != null ? latestSeed.getCoreConflict() : null)
                .worldSetting(latestSeed != null ? latestSeed.getWorldSetting() : null)
                .latestPlanId(latestPlan != null ? latestPlan.getPlanId() : null)
                .totalVolumes(latestPlan != null ? latestPlan.getTotalVolumes() : null)
                .chaptersPerVolume(latestPlan != null ? latestPlan.getChaptersPerVolume() : null)
                .chapterCount(chapterCount)
                .hasPlan(latestPlan != null)
                .createTime(novel.getCreateTime())
                .updateTime(novel.getUpdateTime())
                .build();
    }

    private NovelSeed queryLatestSeed(String novelId) {
        List<NovelSeed> seeds = novelSeedDao.queryByNovelId(novelId);
        return seeds == null || seeds.isEmpty() ? null : seeds.get(0);
    }

    private NovelPlan queryLatestPlan(String novelId) {
        List<NovelPlan> plans = novelPlanDao.queryByNovelId(novelId);
        return plans == null || plans.isEmpty() ? null : plans.get(0);
    }

    private ChapterDetail convertChapter(Chapter chapter) {
        return ChapterDetail.builder()
                .chapterId(chapter.getChapterId())
                .novelId(chapter.getNovelId())
                .volumeNumber(chapter.getVolumeNumber())
                .chapterNumber(chapter.getChapterNumber())
                .title(chapter.getTitle())
                .outline(chapter.getOutline())
                .content(chapter.getContent())
                .wordCount(chapter.getWordCount())
                .status(chapter.getStatus())
                .createTime(chapter.getCreateTime())
                .updateTime(chapter.getUpdateTime())
                .build();
    }

    private SceneDetail convertScene(Scene scene) {
        return SceneDetail.builder()
                .sceneId(scene.getSceneId())
                .sceneNumber(scene.getSceneNumber())
                .sceneTitle(scene.getSceneTitle())
                .sceneType(scene.getSceneType())
                .content(scene.getContent())
                .wordCount(scene.getWordCount())
                .characters(scene.getCharacters())
                .location(scene.getLocation())
                .build();
    }

    private NovelAgentConfigItem convertConfig(NovelAgentConfig config) {
        if (config == null) {
            return null;
        }
        return NovelAgentConfigItem.builder()
                .id(config.getId())
                .configId(config.getConfigId())
                .novelId(config.getNovelId())
                .agentType(config.getAgentType())
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }

    private List<SceneDetail> normalizeScenes(ChapterDetail chapterDetail, String chapterId, String fallbackContent) {
        List<SceneDetail> scenes = chapterDetail.getScenes();
        if (scenes == null || scenes.isEmpty()) {
            return List.of(SceneDetail.builder()
                    .sceneId(UUID.randomUUID().toString())
                    .sceneNumber(1)
                    .sceneTitle(firstNonBlank(chapterDetail.getTitle(), "默认场景"))
                    .sceneType("章节正文")
                    .content(fallbackContent)
                    .wordCount(fallbackContent != null ? fallbackContent.length() : 0)
                    .build());
        }

        List<SceneDetail> normalized = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i++) {
            SceneDetail scene = scenes.get(i);
            normalized.add(SceneDetail.builder()
                    .sceneId(hasText(scene.getSceneId()) ? scene.getSceneId() : UUID.randomUUID().toString())
                    .sceneNumber(scene.getSceneNumber() != null ? scene.getSceneNumber() : i + 1)
                    .sceneTitle(scene.getSceneTitle())
                    .sceneType(scene.getSceneType())
                    .content(scene.getContent())
                    .wordCount(scene.getWordCount())
                    .characters(scene.getCharacters())
                    .location(scene.getLocation())
                    .build());
        }
        return normalized;
    }

    private String mergeSceneContent(List<SceneDetail> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return null;
        }
        return scenes.stream()
                .map(SceneDetail::getContent)
                .filter(this::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
