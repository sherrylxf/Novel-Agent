package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * 校验执行节点
 * 校验生成内容
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class ValidationExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("校验节点：开始校验生成内容");
        
        // 从上下文获取Scene
        Scene scene = context.getAttribute("currentScene");
        
        // 一致性校验
        IAgent<Scene, Boolean> consistencyAgent = orchestrator.getAgent("ConsistencyGuardAgent");
        Boolean consistencyResult = consistencyAgent.execute(scene, context);
        if (!consistencyResult) {
            throw new RuntimeException("一致性校验失败");
        }
        
        // KG规则校验
        IAgent<Scene, Boolean> kgAgent = orchestrator.getAgent("KGRuleValidatorAgent");
        Boolean kgResult = kgAgent.execute(scene, context);
        if (!kgResult) {
            throw new RuntimeException("知识图谱规则校验失败");
        }
        
        context.setCurrentStage(GenerationStage.COMPLETE.getName());
        log.info("校验完成");
        
        // 最后一个节点，返回null
        return null;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return null; // 最后一个节点
    }
    
}
