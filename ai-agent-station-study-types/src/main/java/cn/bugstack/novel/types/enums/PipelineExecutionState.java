package cn.bugstack.novel.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 小说生成 Pipeline 执行生命周期状态（与 {@link GenerationStage} 业务阶段正交）。
 * <p>
 * 用于编排层控制「能否继续执行」「是否已终态」，配合指数退避重试与分步 SSE。
 */
@Getter
@AllArgsConstructor
public enum PipelineExecutionState {

    /** 待调度：已初始化或上一阶段已成功，等待触发下一步 */
    PENDING("待调度"),

    /** 执行中：当前正在执行某一 Handler（单次 executeNextStep 周期内） */
    RUNNING("执行中"),

    /**
     * 阶段成功：本步 Handler 已成功落盘逻辑执行完毕，等待下一次触发以进入后续阶段
     * （与 {@link #PENDING} 区分：PENDING 多指「尚未开始或刚初始化」；二者均可继续执行下一步）
     */
    STAGE_SUCCEEDED("阶段成功"),

    /**
     * 阶段失败：本步抛错后、重试尚未耗尽前的内存态（下一次尝试开始会回到 RUNNING）
     */
    STEP_FAILED("阶段失败"),

    /**
     * 退避重试中：指数退避 sleep 期间，阻止同会话并发再次 {@code executeNextStep}（与口述稿「重试状态」一致）
     */
    RETRYING("重试中"),

    /** 任务成功：责任链无下一节点，业务阶段已进入 COMPLETE */
    COMPLETED("任务成功"),

    /** 任务失败：重试耗尽或不可恢复错误，需新建会话或从库恢复上下文 */
    FAILED("任务失败"),

    ;

    private final String label;
}
