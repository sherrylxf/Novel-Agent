package cn.bugstack.novel.domain.service.novel;

import cn.bugstack.novel.domain.model.entity.ChapterDetail;
import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import cn.bugstack.novel.domain.model.entity.NovelProject;

import java.util.List;

/**
 * 小说工作台服务接口
 */
public interface INovelWorkspaceService {

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
     * 获取上一章正文末尾（用于新章与前一章衔接）
     * @param novelId 小说ID
     * @param volumeNumber 卷号
     * @param currentChapterNumber 当前章节号（将取 currentChapterNumber-1 的末尾）
     * @return 上一章末尾约800字，若无则返回空字符串
     */
    String getPreviousChapterEnding(String novelId, Integer volumeNumber, Integer currentChapterNumber);

    ChapterDetail saveOrUpdateChapter(ChapterDetail chapterDetail);

    void deleteChapter(String chapterId);
}
