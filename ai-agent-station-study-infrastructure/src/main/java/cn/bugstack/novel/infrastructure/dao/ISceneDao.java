package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.Scene;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 场景表 DAO
 */
@Mapper
public interface ISceneDao {

    int insert(Scene scene);

    int updateBySceneId(Scene scene);

    Scene queryBySceneId(String sceneId);

    List<Scene> queryByChapterId(String chapterId);

    Scene queryByChapterIdAndSceneNumber(String chapterId, Integer sceneNumber);
}

