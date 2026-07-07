package cn.bugstack.novel.domain.model.valobj;

/**
 * {@link cn.bugstack.novel.domain.model.entity.NovelContext#attributes} 中使用的键名约定。
 */
public final class NovelContextKeys {

    private NovelContextKeys() {
    }

    /**
     * 值类型：{@link cn.bugstack.novel.domain.model.valobj.NovelAgentRuntimeConfig}
     */
    public static final String AGENT_RUNTIME_CONFIG = "agentRuntimeConfig";

    /**
     * 最近一次阶段失败（重试耗尽或不可恢复错误）的说明，便于前端展示与日志关联。
     */
    public static final String LAST_STAGE_FAILURE_MESSAGE = "lastStageFailureMessage";

    /**
     * HTTP/SSE 会话 ID；供可选的检查点落库关联（{@code novel.pipeline.checkpoint.enabled}）。
     */
    public static final String SESSION_ID = "pipelineSessionId";

    /** 用户总控 Prompt（完整创作约束） */
    public static final String MASTER_PROMPT = "masterPrompt";

    /** 规划总卷数（覆盖默认按章节推算的卷数） */
    public static final String TOTAL_VOLUMES = "totalVolumes";

    /** 单卷目标字数 */
    public static final String VOLUME_TARGET_WORD_COUNT = "volumeTargetWordCount";

    /** 全书目标字数允许误差（默认 10000） */
    public static final String WORD_COUNT_TOLERANCE = "wordCountTolerance";

    /** 最近一次卷完成快照（{@link VolumeCompletionSummary}） */
    public static final String LAST_VOLUME_COMPLETION = "lastVolumeCompletion";
}
