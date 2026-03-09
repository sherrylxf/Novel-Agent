package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 章节表 DAO
 */
@Mapper
public interface IChapterDao {

    int insert(Chapter chapter);

    int updateByChapterId(Chapter chapter);

    int deleteByChapterId(@Param("chapterId") String chapterId);

    Chapter queryByChapterId(@Param("chapterId") String chapterId);

    Chapter queryByNovelIdAndVolumeAndChapter(@Param("novelId") String novelId,
                                              @Param("volumeNumber") Integer volumeNumber,
                                              @Param("chapterNumber") Integer chapterNumber);

    List<Chapter> queryByNovelId(@Param("novelId") String novelId);
}

