package cn.bugstack.novel.domain.model.valobj;

/**
 * 自库加载的 Pipeline 检查点快照（与基础设施 PO 字段对齐，领域层无持久化注解）。
 */
public record NovelPipelineCheckpointSnapshot(
        String novelId,
        String sessionId,
        String currentStage,
        String pipelineExecutionState,
        String lastFailureMessage
) {
}
