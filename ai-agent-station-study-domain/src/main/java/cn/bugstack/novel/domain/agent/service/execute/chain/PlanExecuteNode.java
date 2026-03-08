package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.service.persistence.INovelGenerationStoreService;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * 规划执行节点
 * 规划小说结构
 */
@Slf4j
@Component
public class PlanExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private INovelGenerationStoreService generationStoreService;
    
    @Resource
    private VolumeExecuteNode volumeExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("规划节点：开始规划小说");
        
        // 从上下文获取Seed
        NovelSeed seed = context.getAttribute("seed");
        
        // 执行规划
        IAgent<NovelSeed, NovelPlan> planAgent = orchestrator.getAgent("NovelPlannerAgent");
        NovelPlan plan = planAgent.execute(seed, context);
        if (plan != null && plan.getNovelId() == null) {
            plan.setNovelId(context.getNovelId());
        }
        
        // 保存到上下文
        context.setAttribute("plan", plan);
        
        // 自动落库（不阻塞主流程）
        try {
            generationStoreService.persistPlan(plan);
        } catch (Exception e) {
            log.warn("Plan落库失败，但继续执行，novelId: {}, err: {}", context.getNovelId(), e.getMessage());
        }
        context.setCurrentStage(GenerationStage.VOLUME_PLAN.name());
        
        return volumeExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return volumeExecuteNode;
    }
    
}
