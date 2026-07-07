package cn.bugstack.novel.domain.agent.pipeline;

import cn.bugstack.novel.types.enums.GenerationStage;

import java.util.List;

/**
 * 小说生成责任链的<strong>主链阶段顺序</strong>（不含 ROOT、不含 VALIDATION 后动态回到章/卷的分支）。
 * <p>
 * 实际运行时下一跳由各 {@link cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport#doExecute}
 * 返回的节点决定；本类用于文档、测试与工厂类对外声明顺序。
 */
public final class NovelPipelineTopology {

    /**
     * 典型线性业务阶段（与 {@link GenerationStageStateMachine} 主路径一致）
     */
    public static final List<GenerationStage> PRIMARY_LINEAR_STAGES = List.of(
            GenerationStage.SEED,
            GenerationStage.NOVEL_PLAN,
            GenerationStage.VOLUME_PLAN,
            GenerationStage.CHAPTER_OUTLINE,
            GenerationStage.SCENE_GENERATION,
            GenerationStage.VALIDATION
    );

    private NovelPipelineTopology() {
    }
}
