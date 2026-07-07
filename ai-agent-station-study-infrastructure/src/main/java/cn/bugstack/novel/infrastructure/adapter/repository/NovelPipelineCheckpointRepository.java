package cn.bugstack.novel.infrastructure.adapter.repository;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelPipelineCheckpointRepository;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.valobj.NovelContextKeys;
import cn.bugstack.novel.domain.model.valobj.NovelPipelineCheckpointSnapshot;
import cn.bugstack.novel.infrastructure.dao.INovelPipelineCheckpointDao;
import cn.bugstack.novel.infrastructure.dao.po.NovelPipelineCheckpoint;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 将编排生命周期写入 {@code novel_pipeline_checkpoint}（需建表且开启 {@code novel.pipeline.checkpoint.enabled}）。
 */
@Component
@ConditionalOnProperty(name = "novel.pipeline.checkpoint.enabled", havingValue = "true")
public class NovelPipelineCheckpointRepository implements INovelPipelineCheckpointRepository {

    @Resource
    private INovelPipelineCheckpointDao novelPipelineCheckpointDao;

    @Override
    public void upsert(NovelContext context) {
        if (context == null || context.getNovelId() == null || context.getNovelId().isBlank()) {
            return;
        }
        String sessionId = context.getAttribute(NovelContextKeys.SESSION_ID);
        String lastErr = context.getAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE);
        NovelPipelineCheckpoint row = NovelPipelineCheckpoint.builder()
                .novelId(context.getNovelId())
                .sessionId(sessionId)
                .currentStage(context.getCurrentStage())
                .pipelineExecutionState(
                        context.getPipelineExecutionState() != null ? context.getPipelineExecutionState().name() : null)
                .lastFailureMessage(lastErr)
                .build();
        novelPipelineCheckpointDao.upsert(row);
    }

    @Override
    public Optional<NovelPipelineCheckpointSnapshot> findByNovelId(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            return Optional.empty();
        }
        NovelPipelineCheckpoint po = novelPipelineCheckpointDao.queryByNovelId(novelId);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(new NovelPipelineCheckpointSnapshot(
                po.getNovelId(),
                po.getSessionId(),
                po.getCurrentStage(),
                po.getPipelineExecutionState(),
                po.getLastFailureMessage()));
    }
}
