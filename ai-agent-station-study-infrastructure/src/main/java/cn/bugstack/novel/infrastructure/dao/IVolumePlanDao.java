package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.VolumePlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 卷/册规划表 DAO
 */
@Mapper
public interface IVolumePlanDao {
    
    /**
     * 插入卷规划
     */
    int insert(VolumePlan volumePlan);
    
    /**
     * 批量插入卷规划
     */
    int batchInsert(@Param("list") List<VolumePlan> volumePlans);
    
    /**
     * 根据卷ID更新
     */
    int updateByVolumeId(VolumePlan volumePlan);
    
    /**
     * 根据卷ID查询
     */
    VolumePlan queryByVolumeId(@Param("volumeId") String volumeId);
    
    /**
     * 根据小说ID查询所有卷规划
     */
    List<VolumePlan> queryByNovelId(@Param("novelId") String novelId);
    
    /**
     * 根据小说ID和卷序号查询
     */
    VolumePlan queryByNovelIdAndVolumeNumber(@Param("novelId") String novelId,
                                             @Param("volumeNumber") Integer volumeNumber);
    
    /**
     * 根据小说ID删除所有卷规划
     */
    int deleteByNovelId(@Param("novelId") String novelId);
    
}
