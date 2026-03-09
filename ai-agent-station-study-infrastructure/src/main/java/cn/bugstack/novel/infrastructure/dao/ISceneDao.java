package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.Scene;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 场景表 DAO
 */
@Mapper
public interface ISceneDao {

    int insert(Scene scene);

    int updateBySceneId(Scene scene);

    int deleteBySceneId(@Param("sceneId") String sceneId);

    int deleteByChapterId(@Param("chapterId") String chapterId);

    Scene queryBySceneId(@Param("sceneId") String sceneId);

    List<Scene> queryByChapterId(@Param("chapterId") String chapterId);

    Scene queryByChapterIdAndSceneNumber(@Param("chapterId") String chapterId,
                                         @Param("sceneNumber") Integer sceneNumber);
}

