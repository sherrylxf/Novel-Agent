package cn.bugstack.novel.domain.agent;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent抽象基类
 * 提供通用的日志记录和上下文管理功能
 */
@Slf4j
public abstract class AbstractAgent<T, R> implements IAgent<T, R> {
    
    protected final AgentType agentType;
    
    protected AbstractAgent(AgentType agentType) {
        this.agentType = agentType;
    }
    
    @Override
    public R execute(T input, NovelContext context) {
        log.info("[{}] 开始执行，输入: {}", getName(), input);
        
        try {
            // 执行前校验
            validateBeforeExecute(input, context);
            
            // 执行Agent逻辑
            R result = doExecute(input, context);
            
            // 执行后处理
            afterExecute(result, context);
            
            log.info("[{}] 执行完成，结果: {}", getName(), result);
            return result;
            
        } catch (Exception e) {
            log.error("[{}] 执行失败", getName(), e);
            throw new RuntimeException("Agent执行失败: " + getName(), e);
        }
    }
    
    /**
     * 执行前校验（子类可重写）
     */
    protected void validateBeforeExecute(T input, NovelContext context) {
        // 默认不做校验，子类可重写
    }
    
    /**
     * 执行Agent核心逻辑（子类必须实现）
     */
    protected abstract R doExecute(T input, NovelContext context);
    
    /**
     * 执行后处理（子类可重写）
     */
    protected void afterExecute(R result, NovelContext context) {
        // 默认不做处理，子类可重写
    }
    
    @Override
    public String getName() {
        return agentType.getDescription();
    }
    
    @Override
    public String getType() {
        return agentType.getCode();
    }
    
}
