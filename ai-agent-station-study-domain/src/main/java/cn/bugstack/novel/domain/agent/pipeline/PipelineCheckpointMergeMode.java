package cn.bugstack.novel.domain.agent.pipeline;

/**
 * 检查点合并策略：新建生成会话与「续写上下文已按业务表推导」两种场景对 currentStage 的优先级不同。
 */
public enum PipelineCheckpointMergeMode {

    /**
     * 初始化会话后：以检查点为准恢复末次阶段与状态（进程崩溃、换 session 同 novelId）。
     */
    FRESH_SESSION,

    /**
     * {@link cn.bugstack.novel.domain.service.novel.INovelContinuationService} 已根据大纲/章节推导阶段后：
     * 仅当检查点处于失败/重试/运行中半态时覆盖阶段，避免把续写指针拉回到陈旧检查点。
     */
    AFTER_CONTINUATION
}
