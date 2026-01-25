package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * 卷规划执行节点
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class VolumeExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private ChapterExecuteNode chapterExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("卷规划节点：开始规划卷/册");
        
        // 从上下文获取Plan
        NovelPlan plan = context.getAttribute("plan");
        
        // 执行卷规划（规划第一卷）
        IAgent<Object[], VolumePlan> volumeAgent = orchestrator.getAgent("VolumePlannerAgent");
        VolumePlan volumePlan = volumeAgent.execute(new Object[]{plan, 1}, context);
        
        // 保存到上下文
        context.setAttribute("currentVolume", volumePlan);
        context.setCurrentStage(GenerationStage.CHAPTER_OUTLINE.getName());
        
        return chapterExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return chapterExecuteNode;
    }
    
}
