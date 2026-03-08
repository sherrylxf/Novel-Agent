package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.persistence.INovelGenerationStoreService;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.util.ArrayList;

/**
 * 卷规划执行节点
 */
@Slf4j
@Component
public class VolumeExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private INovelGenerationStoreService generationStoreService;
    
    @Resource
    private ChapterExecuteNode chapterExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("卷规划节点：开始规划卷/册");
        
        // 从上下文获取Plan与当前卷序号（多章循环时由 Validation 回填）
        NovelPlan plan = context.getAttribute("plan");
        Integer volumeNumber = context.getAttribute("currentVolumeNumber");
        if (volumeNumber == null) volumeNumber = 1;
        
        // 执行卷规划
        IAgent<Object[], VolumePlan> volumeAgent = orchestrator.getAgent("VolumePlannerAgent");
        VolumePlan volumePlan = volumeAgent.execute(new Object[]{plan, volumeNumber}, context);
        if (volumePlan != null && volumePlan.getNovelId() == null) {
            volumePlan.setNovelId(plan != null ? plan.getNovelId() : context.getNovelId());
        }
        
        // 保存到上下文（供 Plot Tracker / 多章循环使用）
        context.setAttribute("currentVolume", volumePlan);
        context.setAttribute("currentVolumeNumber", volumePlan != null ? volumePlan.getVolumeNumber() : volumeNumber);
        // 同步到 plan.volumePlans，便于后续保存/展示
        if (plan != null) {
            if (plan.getVolumePlans() == null) {
                plan.setVolumePlans(new ArrayList<>());
            }
            plan.getVolumePlans().removeIf(v -> v != null && v.getVolumeNumber() != null
                    && volumePlan != null && v.getVolumeNumber().equals(volumePlan.getVolumeNumber()));
            plan.getVolumePlans().add(volumePlan);
        }
        
        // 自动落库（不阻塞主流程）
        try {
            generationStoreService.persistVolumePlan(context.getNovelId(), volumePlan);
        } catch (Exception e) {
            log.warn("VolumePlan落库失败，但继续执行，novelId: {}, err: {}", context.getNovelId(), e.getMessage());
        }
        context.setCurrentStage(GenerationStage.CHAPTER_OUTLINE.name());
        
        return chapterExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return chapterExecuteNode;
    }
    
}
