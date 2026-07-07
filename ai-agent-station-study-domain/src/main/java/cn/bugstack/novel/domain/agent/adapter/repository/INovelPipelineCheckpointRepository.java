package cn.bugstack.novel.domain.agent.adapter.repository;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.valobj.NovelPipelineCheckpointSnapshot;

import java.util.Optional;

/**
 * 将 {@link NovelContext} 中的阶段与 Pipeline 生命周期状态持久化到库（可选启用），
 * 便于进程重启后观测末次执行点；与业务表推导续写（{@code NovelContinuationService}）可并存。
 */
public interface INovelPipelineCheckpointRepository {

    void upsert(NovelContext context);

    Optional<NovelPipelineCheckpointSnapshot> findByNovelId(String novelId);
}
