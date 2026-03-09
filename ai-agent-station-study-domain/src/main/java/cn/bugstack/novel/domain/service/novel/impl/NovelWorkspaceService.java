package cn.bugstack.novel.domain.service.novel.impl;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelWorkspaceRepository;
import cn.bugstack.novel.domain.model.entity.ChapterDetail;
import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import cn.bugstack.novel.domain.model.entity.NovelProject;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 小说工作台服务
 */
@Slf4j
@Service
public class NovelWorkspaceService implements INovelWorkspaceService {

    private final INovelWorkspaceRepository novelWorkspaceRepository;

    public NovelWorkspaceService(INovelWorkspaceRepository novelWorkspaceRepository) {
        this.novelWorkspaceRepository = novelWorkspaceRepository;
    }

    @Override
    public NovelProject saveOrUpdateNovel(NovelProject novelProject) {
        log.info("保存小说项目，novelId={}", novelProject != null ? novelProject.getNovelId() : null);
        return novelWorkspaceRepository.saveOrUpdateNovel(novelProject);
    }

    @Override
    public List<NovelProject> queryNovelProjects() {
        return novelWorkspaceRepository.queryNovelProjects();
    }

    @Override
    public NovelProject queryNovelProject(String novelId) {
        return novelWorkspaceRepository.queryNovelProject(novelId);
    }

    @Override
    public void archiveNovel(String novelId) {
        novelWorkspaceRepository.archiveNovel(novelId);
    }

    @Override
    public NovelAgentConfigItem saveOrUpdateConfig(NovelAgentConfigItem configItem) {
        return novelWorkspaceRepository.saveOrUpdateConfig(configItem);
    }

    @Override
    public List<NovelAgentConfigItem> queryConfigs(String novelId, boolean includeGlobal) {
        return novelWorkspaceRepository.queryConfigs(novelId, includeGlobal);
    }

    @Override
    public void deleteConfig(String configId) {
        novelWorkspaceRepository.deleteConfig(configId);
    }

    @Override
    public List<ChapterDetail> queryChapters(String novelId) {
        return novelWorkspaceRepository.queryChapters(novelId);
    }

    @Override
    public ChapterDetail queryChapterDetail(String chapterId) {
        return novelWorkspaceRepository.queryChapterDetail(chapterId);
    }

    @Override
    public ChapterDetail saveOrUpdateChapter(ChapterDetail chapterDetail) {
        return novelWorkspaceRepository.saveOrUpdateChapter(chapterDetail);
    }

    @Override
    public void deleteChapter(String chapterId) {
        novelWorkspaceRepository.deleteChapter(chapterId);
    }
}
