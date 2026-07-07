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

    /**
     * 查询所有小说（含已归档），用于阅读页
     */
    List<NovelProject> queryAllNovelProjects();

    NovelProject queryNovelProject(String novelId);

    void archiveNovel(String novelId);

    NovelAgentConfigItem saveOrUpdateConfig(NovelAgentConfigItem configItem);

    List<NovelAgentConfigItem> queryConfigs(String novelId, boolean includeGlobal);

    void deleteConfig(String configId);

    List<ChapterDetail> queryChapters(String novelId);

    ChapterDetail queryChapterDetail(String chapterId);

    /**
     * 按小说ID、卷号、章节号查询章节正文（用于前后章衔接）
     */
    String getChapterContent(String novelId, Integer volumeNumber, Integer chapterNumber);

    ChapterDetail saveOrUpdateChapter(ChapterDetail chapterDetail);

    void deleteChapter(String chapterId);
}
