package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.NovelPipelineCheckpoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 小说生成 Pipeline 检查点
 */
@Mapper
public interface INovelPipelineCheckpointDao {

    int upsert(NovelPipelineCheckpoint row);

    NovelPipelineCheckpoint queryByNovelId(@Param("novelId") String novelId);
}
