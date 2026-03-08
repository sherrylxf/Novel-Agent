package cn.bugstack.novel.domain.service.novel;

import cn.bugstack.novel.domain.model.entity.NovelPlan;

/**
 * 小说规划服务接口
 */
public interface INovelPlanService {
    
    /**
     * 保存小说规划
     *
     * @param novelPlan 小说规划
     */
    void saveNovelPlan(NovelPlan novelPlan);
    
    /**
     * 根据规划ID查询
     *
     * @param planId 规划ID
     * @return 小说规划
     */
    NovelPlan queryByPlanId(String planId);
    
    /**
     * 根据小说ID查询最新规划
     *
     * @param novelId 小说ID
     * @return 小说规划
     */
    NovelPlan queryByNovelId(String novelId);
    
    /**
     * 更新小说规划
     *
     * @param novelPlan 小说规划
     */
    void updateNovelPlan(NovelPlan novelPlan);
    
}
