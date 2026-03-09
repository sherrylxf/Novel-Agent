package cn.bugstack.novel.domain.agent.adapter.repository;

import cn.bugstack.novel.domain.model.entity.ChapterDetail;
import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import cn.bugstack.novel.domain.model.entity.NovelProject;

import java.util.List;

/**
 * 小说工作台仓储接口
 */
public interface INovelWorkspaceRepository {

    NovelProject saveOrUpdateNovel(NovelProject novelProject);

    List<NovelProject> queryNovelProjects();

    NovelProject queryNovelProject(String novelId);

    void archiveNovel(String novelId);

    NovelAgentConfigItem saveOrUpdateConfig(NovelAgentConfigItem configItem);

    List<NovelAgentConfigItem> queryConfigs(String novelId, boolean includeGlobal);

    void deleteConfig(String configId);

    List<ChapterDetail> queryChapters(String novelId);

    ChapterDetail queryChapterDetail(String chapterId);

    ChapterDetail saveOrUpdateChapter(ChapterDetail chapterDetail);

    void deleteChapter(String chapterId);
}
