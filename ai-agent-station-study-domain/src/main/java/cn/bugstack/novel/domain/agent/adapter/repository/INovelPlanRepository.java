package cn.bugstack.novel.domain.agent.adapter.repository;

import cn.bugstack.novel.domain.model.entity.NovelPlan;

import java.util.List;

/**
 * 小说规划仓储接口
 * 领域层定义的仓储接口，由基础设施层实现
 */
public interface INovelPlanRepository {
    
    /**
     * 保存小说规划
     */
    void saveNovelPlan(NovelPlan novelPlan);
    
    /**
     * 根据规划ID查询
     */
    NovelPlan queryByPlanId(String planId);
    
    /**
     * 根据小说ID查询
     */
    NovelPlan queryByNovelId(String novelId);
    
    /**
     * 根据小说ID查询所有规划（历史版本）
     */
    List<NovelPlan> queryAllByNovelId(String novelId);
    
    /**
     * 更新小说规划
     */
    void updateNovelPlan(NovelPlan novelPlan);
    
}
