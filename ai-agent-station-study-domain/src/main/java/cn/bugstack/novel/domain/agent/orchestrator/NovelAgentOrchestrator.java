package cn.bugstack.novel.domain.agent.orchestrator;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.service.execute.chain.RootExecuteNode;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Novel Agent编排器
 * 负责协调多个Agent的执行流程
 * 参考课程第3-10、11节：执行链路设计
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class NovelAgentOrchestrator {
    
    // Agent注册表
    private final Map<String, IAgent<?, ?>> agents = new HashMap<>();
    
    @Resource
    private RootExecuteNode rootExecuteNode;
    
    /**
     * 注册Agent
     */
    public void registerAgent(String agentType, IAgent<?, ?> agent) {
        agents.put(agentType, agent);
        log.info("注册Agent: {} -> {}", agentType, agent.getName());
    }
    
    /**
     * 获取Agent
     */
    @SuppressWarnings("unchecked")
    public <T, R> IAgent<T, R> getAgent(String agentType) {
        IAgent<?, ?> agent = agents.get(agentType);
        if (agent == null) {
            throw new RuntimeException("Agent not found: " + agentType);
        }
        return (IAgent<T, R>) agent;
    }
    
    /**
     * 获取Agent数量
     */
    public int getAgentCount() {
        return agents.size();
    }
    
    /**
     * 执行完整的小说生成流程（使用责任链模式）
     * Seed → 梗概 → 场景 → 正文
     */
    public NovelPlan generateNovel(String genre, String coreConflict, String worldSetting) {
        log.info("开始生成小说，题材: {}, 核心冲突: {}", genre, coreConflict);
        
        // 创建上下文
        NovelContext context = NovelContext.builder()
                .novelId("novel-" + System.currentTimeMillis())
                .currentStage(GenerationStage.SEED.getName())
                .build();
        
        // 设置参数到上下文
        context.setAttribute("genre", genre);
        context.setAttribute("coreConflict", coreConflict);
        context.setAttribute("worldSetting", worldSetting);
        
        try {
            // 使用责任链模式执行
            rootExecuteNode.execute(context);
            
            // 获取结果
            NovelPlan plan = context.getAttribute("plan");
            
            context.setCurrentStage(GenerationStage.COMPLETE.getName());
            log.info("小说生成完成，小说ID: {}", context.getNovelId());
            
            return plan;
            
        } catch (Exception e) {
            log.error("小说生成失败", e);
            throw new RuntimeException("小说生成失败", e);
        }
    }
    
}
