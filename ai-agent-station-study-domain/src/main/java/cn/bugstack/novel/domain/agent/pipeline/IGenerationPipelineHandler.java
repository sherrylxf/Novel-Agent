package cn.bugstack.novel.domain.agent.pipeline;

import cn.bugstack.novel.domain.model.entity.NovelContext;

/**
 * 小说生成流水线统一 Handler 接口（责任链中的一环）。
 * <p>
 * 等价于「execute + 下一跳」：由 {@link #executeStageStep} 执行当前阶段逻辑，
 * 返回值即为下一节点。生产环境下一跳主要由各 Spring Bean 在 {@code doExecute} 内显式 return，
 * 拓扑汇总见 {@link GenerationPipelineFactory}；{@link #setNextHandler} 为可选扩展点，
 * 默认无操作，{@link cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport} 中可覆盖下一跳。
 * <p>
 * 与 {@link cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport} 配合，
 * 将「阶段执行」与「下一跳解析」显式化，便于扩展与测试。
 */
public interface IGenerationPipelineHandler {

    /**
     * 处理器名称（日志与排障）
     */
    String handlerName();

    /**
     * 分步执行本阶段；返回责任链上下一跳 Handler（若无则 null 表示链路结束）。
     */
    IGenerationPipelineHandler executeStageStep(NovelContext context);

    /**
     * 设置下一处理器；默认无操作。常规路径由 {@code ExecuteNode} 的返回值串联，本方法便于测试桩或动态装配。
     */
    default void setNextHandler(IGenerationPipelineHandler next) {
    }
}
