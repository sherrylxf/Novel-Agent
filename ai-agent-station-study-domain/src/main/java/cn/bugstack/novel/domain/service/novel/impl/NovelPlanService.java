package cn.bugstack.novel.domain.service.novel.impl;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelPlanRepository;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.service.novel.INovelPlanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 小说规划服务实现
 */
@Slf4j
@Service
public class NovelPlanService implements INovelPlanService {
    
    private final INovelPlanRepository novelPlanRepository;
    
    public NovelPlanService(INovelPlanRepository novelPlanRepository) {
        this.novelPlanRepository = novelPlanRepository;
    }
    
    @Override
    public void saveNovelPlan(NovelPlan novelPlan) {
        log.info("保存小说规划，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId());
        novelPlanRepository.saveNovelPlan(novelPlan);
    }
    
    @Override
    public NovelPlan queryByPlanId(String planId) {
        log.info("根据规划ID查询，planId: {}", planId);
        return novelPlanRepository.queryByPlanId(planId);
    }
    
    @Override
    public NovelPlan queryByNovelId(String novelId) {
        log.info("根据小说ID查询规划，novelId: {}", novelId);
        return novelPlanRepository.queryByNovelId(novelId);
    }
    
    @Override
    public void updateNovelPlan(NovelPlan novelPlan) {
        log.info("更新小说规划，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId());
        novelPlanRepository.updateNovelPlan(novelPlan);
    }
    
}
