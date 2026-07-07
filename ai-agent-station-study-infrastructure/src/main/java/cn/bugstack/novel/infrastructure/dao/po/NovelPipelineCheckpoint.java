package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pipeline 执行检查点（与业务章节数据正交，仅记录末次编排状态）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelPipelineCheckpoint {

    private Long id;
    private String novelId;
    private String sessionId;
    private String currentStage;
    private String pipelineExecutionState;
    private String lastFailureMessage;
    private LocalDateTime updateTime;
}
