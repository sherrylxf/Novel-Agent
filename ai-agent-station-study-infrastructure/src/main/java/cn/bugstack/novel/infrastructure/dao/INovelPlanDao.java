package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.NovelPlan;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 小说规划表 DAO
 */
@Mapper
public interface INovelPlanDao {
    
    /**
     * 插入小说规划
     */
    int insert(NovelPlan novelPlan);
    
    /**
     * 根据规划ID更新
     */
    int updateByPlanId(NovelPlan novelPlan);
    
    /**
     * 根据规划ID查询
     */
    NovelPlan queryByPlanId(String planId);
    
    /**
     * 根据小说ID查询
     */
    List<NovelPlan> queryByNovelId(String novelId);
    
}
