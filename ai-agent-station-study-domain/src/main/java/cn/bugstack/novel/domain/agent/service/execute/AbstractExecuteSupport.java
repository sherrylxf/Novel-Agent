package cn.bugstack.novel.domain.agent.service.execute;

import cn.bugstack.novel.domain.agent.pipeline.IGenerationPipelineHandler;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 执行支撑抽象类：责任链节点骨架。
 * <p>
 * 子类在 {@link #doExecute} 中完成 prompt 构建、调用 {@link cn.bugstack.novel.domain.agent.IAgent}、
 * 校验、落库等，并 <strong>return 下一节点</strong> 完成链的串联。
 * {@link #setNextHandler} 仅在 {@link #executeStageStep} 路径生效；一次性 {@link #execute} 仍以 {@code doExecute} 返回值递归。
 */
@Slf4j
public abstract class AbstractExecuteSupport implements IGenerationPipelineHandler {

    /**
     * 可选：覆盖 {@link #doExecute} 返回的下一跳（测试桩、A/B 或动态插阶段）。
     * 生产路径通常不设置，由子类 {@code return} 下一节点即可。
     */
    private volatile IGenerationPipelineHandler nextHandlerOverride;

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

    @Override
    public String handlerName() {
        return getClass().getSimpleName();
    }

    @Override
    public void setNextHandler(IGenerationPipelineHandler next) {
        this.nextHandlerOverride = next;
    }

    @Override
    public IGenerationPipelineHandler executeStageStep(NovelContext context) {
        AbstractExecuteSupport computed = executeStep(context);
        IGenerationPipelineHandler override = nextHandlerOverride;
        if (override != null) {
            return override;
        }
        return computed;
    }
    
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
