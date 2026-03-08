package cn.bugstack.novel.domain.agent.service.execute;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行支撑抽象类
 * 参考课程第3-10、11节：执行链路设计
 * 使用责任链模式实现执行链路
 */
@Slf4j
public abstract class AbstractExecuteSupport {
    
    /**
     * 执行当前节点逻辑
     *
     * @param context 上下文
     * @return 下一个节点（责任链模式）
     */
    protected abstract AbstractExecuteSupport doExecute(NovelContext context);
    
    /**
     * 获取下一个节点
     *
     * @param context 上下文
     * @return 下一个节点
     */
    public abstract AbstractExecuteSupport getNext(NovelContext context);
    
    /**
     * 执行节点（责任链入口）
     * 会自动执行下一个节点
     */
    public AbstractExecuteSupport execute(NovelContext context) {
        log.info("[{}] 开始执行节点", getClass().getSimpleName());
        
        try {
            // 执行当前节点逻辑
            AbstractExecuteSupport next = doExecute(context);
            
            // 如果有下一个节点，继续执行
            if (next != null) {
                return next.execute(context);
            }
            
            log.info("[{}] 执行完成，无下一个节点", getClass().getSimpleName());
            return null;
            
        } catch (Exception e) {
            log.error("[{}] 执行失败", getClass().getSimpleName(), e);
            throw new RuntimeException("节点执行失败: " + getClass().getSimpleName(), e);
        }
    }
    
    /**
     * 执行当前节点（不自动执行下一个节点）
     * 用于分步执行模式，每次只执行一个节点
     * 
     * @param context 上下文
     * @return 下一个节点（如果存在）
     */
    public AbstractExecuteSupport executeStep(NovelContext context) {
        log.info("[{}] 开始执行节点（分步模式）", getClass().getSimpleName());
        
        try {
            // 执行当前节点逻辑，但不自动执行下一个节点
            return doExecute(context);
            
        } catch (Exception e) {
            log.error("[{}] 执行失败", getClass().getSimpleName(), e);
            throw new RuntimeException("节点执行失败: " + getClass().getSimpleName(), e);
        }
    }
    
}
