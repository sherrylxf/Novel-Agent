package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * Seed执行节点
 * 生成小说Seed
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class SeedExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private PlanExecuteNode planExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("Seed节点：开始生成Seed");
        
        // 从上下文获取参数
        String genre = context.getAttribute("genre");
        String coreConflict = context.getAttribute("coreConflict");
        String worldSetting = context.getAttribute("worldSetting");
        
        // 执行Seed生成
        IAgent<String, NovelSeed> seedAgent = orchestrator.getAgent("NovelSeedAgent");
        String input = String.format("题材: %s\n核心冲突: %s\n世界观: %s", genre, coreConflict, worldSetting);
        NovelSeed seed = seedAgent.execute(input, context);
        
        // 保存到上下文
        context.setAttribute("seed", seed);
        context.setCurrentStage(GenerationStage.NOVEL_PLAN.getName());
        
        return planExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return planExecuteNode;
    }
    
}
