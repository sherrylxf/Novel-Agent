package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.Chapter;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 章节表 DAO
 */
@Mapper
public interface IChapterDao {

    int insert(Chapter chapter);

    int updateByChapterId(Chapter chapter);

    Chapter queryByChapterId(String chapterId);

    Chapter queryByNovelIdAndVolumeAndChapter(String novelId, Integer volumeNumber, Integer chapterNumber);

    List<Chapter> queryByNovelId(String novelId);
}

