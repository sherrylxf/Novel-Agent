package cn.bugstack.novel.domain.agent.orchestrator;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.adapter.repository.INovelPipelineCheckpointRepository;
import cn.bugstack.novel.domain.agent.pipeline.GenerationStageStateMachine;
import cn.bugstack.novel.domain.agent.pipeline.PipelineCheckpointMergeMode;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.agent.service.execute.StageExecutionRetryPolicy;
import cn.bugstack.novel.domain.agent.service.execute.chain.RootExecuteNode;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.valobj.NovelContextKeys;
import cn.bugstack.novel.domain.model.valobj.NovelPipelineCheckpointSnapshot;
import cn.bugstack.novel.domain.model.valobj.NovelAgentRuntimeConfig;
import cn.bugstack.novel.domain.service.config.INovelAgentRuntimeConfigLoader;
import cn.bugstack.novel.types.enums.GenerationStage;
import cn.bugstack.novel.types.enums.PipelineExecutionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Novel Agent编排器
 * 负责协调多个Agent的执行流程
 * 参考课程第3-10、11节：执行链路设计
 */
@Slf4j
@Component
public class NovelAgentOrchestrator {
    
    // Agent注册表
    private final Map<String, IAgent<?, ?>> agents = new HashMap<>();
    
    @Resource
    private RootExecuteNode rootExecuteNode;
    
    @Resource
    private cn.bugstack.novel.domain.agent.service.execute.chain.SeedExecuteNode seedExecuteNode;
    
    @Resource
    private cn.bugstack.novel.domain.agent.service.execute.chain.PlanExecuteNode planExecuteNode;
    
    @Resource
    private cn.bugstack.novel.domain.agent.service.execute.chain.VolumeExecuteNode volumeExecuteNode;
    
    @Resource
    private cn.bugstack.novel.domain.agent.service.execute.chain.ChapterExecuteNode chapterExecuteNode;
    
    @Resource
    private cn.bugstack.novel.domain.agent.service.execute.chain.SceneExecuteNode sceneExecuteNode;
    
    @Resource
    private cn.bugstack.novel.domain.agent.service.execute.chain.ValidationExecuteNode validationExecuteNode;

    @Resource
    private StageExecutionRetryPolicy stageExecutionRetryPolicy;

    @Resource
    private ObjectProvider<INovelAgentRuntimeConfigLoader> agentRuntimeConfigLoaderProvider;

    @Resource
    private ObjectProvider<INovelPipelineCheckpointRepository> pipelineCheckpointRepositoryProvider;
    
    /**
     * 注册Agent
     */
    public void registerAgent(String agentType, IAgent<?, ?> agent) {
        agents.put(agentType, agent);
        log.info("注册Agent: {} -> {}", agentType, agent.getName());
    }
    
    /**
     * 获取Agent
     */
    @SuppressWarnings("unchecked")
    public <T, R> IAgent<T, R> getAgent(String agentType) {
        IAgent<?, ?> agent = agents.get(agentType);
        if (agent == null) {
            throw new RuntimeException("Agent not found: " + agentType);
        }
        return (IAgent<T, R>) agent;
    }
    
    /**
     * 获取Agent数量
     */
    public int getAgentCount() {
        return agents.size();
    }
    
    /**
     * 执行完整的小说生成流程（使用责任链模式）
     * Seed → 梗概 → 场景 → 正文
     * 
     * @deprecated 使用分步执行方法 {@link #executeNextStep(NovelContext)} 替代
     */
    @Deprecated
    public NovelPlan generateNovel(String genre, String coreConflict, String worldSetting) {
        log.info("开始生成小说，题材: {}, 核心冲突: {}", genre, coreConflict);
        
        // 创建上下文
        NovelContext context = NovelContext.builder()
                .novelId("novel-" + System.currentTimeMillis())
                .currentStage(GenerationStage.SEED.getName())
                .pipelineExecutionState(PipelineExecutionState.PENDING)
                .build();
        
        // 设置参数到上下文
        context.setAttribute("genre", genre);
        context.setAttribute("coreConflict", coreConflict);
        context.setAttribute("worldSetting", worldSetting);

        refreshAgentRuntimeConfig(context);

        try {
            // 使用责任链模式执行
            rootExecuteNode.execute(context);
            
            // 获取结果
            NovelPlan plan = context.getAttribute("plan");
            
            context.setCurrentStage(GenerationStage.COMPLETE.getName());
            context.setPipelineExecutionState(PipelineExecutionState.COMPLETED);
            log.info("小说生成完成，小说ID: {}", context.getNovelId());
            
            return plan;
            
        } catch (Exception e) {
            log.error("小说生成失败", e);
            throw new RuntimeException("小说生成失败", e);
        }
    }
    
    /**
     * 初始化上下文（首次调用时）
     * @param targetWordCount 目标总字数（可选，null则使用默认100万字）
     * @param chaptersTotal 总章节数（一键整本时使用）
     * @param wordsPerChapter 每章字数（一键整本时使用，默认3000）
     */
    public NovelContext initializeContext(String genre, String coreConflict, String worldSetting,
                                         String novelId, Integer targetWordCount,
                                         Integer chaptersTotal, Integer wordsPerChapter) {
        NovelContext context = NovelContext.builder()
                .novelId(novelId != null ? novelId : "novel-" + System.currentTimeMillis())
                .currentStage(GenerationStage.SEED.name())
                .pipelineExecutionState(PipelineExecutionState.PENDING)
                .build();

        context.setAttribute("genre", genre);
        context.setAttribute("coreConflict", coreConflict);
        context.setAttribute("worldSetting", worldSetting);
        context.setAttribute("targetWordCount", targetWordCount);
        context.setAttribute("chaptersTotal", chaptersTotal);
        context.setAttribute("wordsPerChapter", wordsPerChapter);

        if (chaptersTotal != null && chaptersTotal > 0 && wordsPerChapter != null && wordsPerChapter > 0) {
            context.setAttribute("targetWordCount", chaptersTotal * wordsPerChapter);
        }
        log.info("初始化上下文，novelId: {}, stage: {}, targetWordCount: {}, chaptersTotal: {}, wordsPerChapter: {}",
                context.getNovelId(), context.getCurrentStage(), context.getAttribute("targetWordCount"), chaptersTotal, wordsPerChapter);
        return context;
    }
    
    /**
     * 执行下一步（分步执行）
     * 根据当前阶段，执行下一个节点，返回执行结果
     * 
     * @param context 上下文
     * @return 执行结果
     */
    public StepExecutionResult executeNextStep(NovelContext context) {
        try {
            if (context != null && context.getPipelineExecutionState() == null) {
                context.setPipelineExecutionState(PipelineExecutionState.PENDING);
            }
            Optional<String> blocked = preflightExecutable(context);
            if (blocked.isPresent()) {
                return StepExecutionResult.builder()
                        .success(false)
                        .message(blocked.get())
                        .currentStage(context != null ? context.getCurrentStage() : null)
                        .pipelineExecutionState(context != null ? context.getPipelineExecutionState() : null)
                        .build();
            }

            StageExecutionRetryPolicy policy = stageExecutionRetryPolicy != null
                    ? stageExecutionRetryPolicy
                    : StageExecutionRetryPolicy.builder().build();
            int maxAttempts = Math.max(1, policy.getMaxAttempts());
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return executeNextStepOnce(context);
                } catch (Exception e) {
                    log.warn("阶段执行失败 [{}/{}]，将指数退避重试: {}", attempt, maxAttempts, e.getMessage());
                    if (attempt >= maxAttempts) {
                        log.error("执行节点失败（已达最大重试），当前阶段: {}", context.getCurrentStage(), e);
                        context.setPipelineExecutionState(PipelineExecutionState.FAILED);
                        context.setAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE, e.getMessage());
                        return StepExecutionResult.builder()
                                .success(false)
                                .message("执行失败: " + e.getMessage())
                                .currentStage(context.getCurrentStage())
                                .pipelineExecutionState(PipelineExecutionState.FAILED)
                                .build();
                    }
                    context.setPipelineExecutionState(PipelineExecutionState.RETRYING);
                    maybePersistCheckpoint(context);
                    long sleepMs = policy.delayAfterAttemptMs(attempt + 1);
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            context.setPipelineExecutionState(PipelineExecutionState.FAILED);
                            context.setAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE, "执行被中断");
                            return StepExecutionResult.builder()
                                    .success(false)
                                    .message("执行被中断")
                                    .currentStage(context.getCurrentStage())
                                    .pipelineExecutionState(PipelineExecutionState.FAILED)
                                    .build();
                        }
                    }
                    context.setPipelineExecutionState(PipelineExecutionState.PENDING);
                    maybePersistCheckpoint(context);
                }
            }
            context.setPipelineExecutionState(PipelineExecutionState.FAILED);
            context.setAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE, "执行失败：重试耗尽");
            return StepExecutionResult.builder()
                    .success(false)
                    .message("执行失败：重试耗尽")
                    .currentStage(context.getCurrentStage())
                    .pipelineExecutionState(PipelineExecutionState.FAILED)
                    .build();
        } finally {
            maybePersistCheckpoint(context);
        }
    }

    /**
     * 执行前校验：终态任务不再推进，避免重复执行、乱序执行。
     */
    private Optional<String> preflightExecutable(NovelContext context) {
        if (context == null) {
            return Optional.of("上下文为空");
        }
        PipelineExecutionState s = context.getPipelineExecutionState();
        if (s == PipelineExecutionState.COMPLETED) {
            return Optional.of("任务已完成，无需继续执行");
        }
        if (s == PipelineExecutionState.FAILED) {
            return Optional.of("任务已失败：可调用 POST /api/v1/novel/pipeline/reset-failed 同会话重试当前阶段，"
                    + "或换新 session 并携带 novelId 以从检查点恢复（无需全量重跑）");
        }
        if (s == PipelineExecutionState.RETRYING) {
            return Optional.of("任务退避重试中，请稍候再触发下一步");
        }
        return Optional.empty();
    }

    private void maybePersistCheckpoint(NovelContext context) {
        INovelPipelineCheckpointRepository repo = pipelineCheckpointRepositoryProvider.getIfAvailable();
        if (repo == null || context == null || context.getNovelId() == null || context.getNovelId().isBlank()) {
            return;
        }
        try {
            repo.upsert(context);
        } catch (Exception e) {
            log.warn("持久化 Pipeline 检查点失败（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 自库合并检查点：新建生成流在 {@link #initializeContext} 之后调用；
     * 续写流在 {@link cn.bugstack.novel.domain.service.novel.INovelContinuationService#buildResumeContext} 之后调用。
     */
    public void mergePersistedCheckpoint(NovelContext context, PipelineCheckpointMergeMode mode) {
        INovelPipelineCheckpointRepository repo = pipelineCheckpointRepositoryProvider.getIfAvailable();
        if (repo == null || context == null || context.getNovelId() == null || context.getNovelId().isBlank()) {
            return;
        }
        Optional<NovelPipelineCheckpointSnapshot> snap = repo.findByNovelId(context.getNovelId());
        if (snap.isEmpty()) {
            return;
        }
        NovelPipelineCheckpointSnapshot cp = snap.get();
        PipelineExecutionState persisted = parsePersistedPipelineState(cp.pipelineExecutionState());
        if (mode == PipelineCheckpointMergeMode.AFTER_CONTINUATION
                && !shouldOverlayStageAfterContinuation(persisted)) {
            return;
        }
        boolean overlayStage = mode == PipelineCheckpointMergeMode.FRESH_SESSION
                || shouldOverlayStageAfterContinuation(persisted);
        if (overlayStage && cp.currentStage() != null && !cp.currentStage().isBlank()) {
            context.setCurrentStage(cp.currentStage());
        }
        PipelineExecutionState normalized = normalizeCheckpointPipelineState(persisted);
        context.setPipelineExecutionState(normalized);
        if (cp.lastFailureMessage() != null && !cp.lastFailureMessage().isBlank()) {
            context.setAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE, cp.lastFailureMessage());
        }
        log.info("已合并 Pipeline 检查点 novelId={}, mode={}, stage={}, state={} -> {}",
                context.getNovelId(), mode, cp.currentStage(), cp.pipelineExecutionState(), normalized);
    }

    /**
     * 同会话内将 FAILED 清为 PENDING，保留 {@link NovelContext#getCurrentStage()}，用于阶段级手动重试（仍走指数退避）。
     */
    public boolean resetFailedPipelineToRetryable(NovelContext context) {
        if (context == null || context.getPipelineExecutionState() != PipelineExecutionState.FAILED) {
            return false;
        }
        context.setPipelineExecutionState(PipelineExecutionState.PENDING);
        context.removeAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE);
        maybePersistCheckpoint(context);
        log.info("已重置 FAILED -> PENDING，准备同阶段重试 novelId={}, stage={}",
                context.getNovelId(), context.getCurrentStage());
        return true;
    }

    private static boolean shouldOverlayStageAfterContinuation(PipelineExecutionState persisted) {
        if (persisted == null) {
            return false;
        }
        return persisted == PipelineExecutionState.FAILED
                || persisted == PipelineExecutionState.RETRYING
                || persisted == PipelineExecutionState.RUNNING
                || persisted == PipelineExecutionState.STEP_FAILED;
    }

    private static PipelineExecutionState parsePersistedPipelineState(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PipelineExecutionState.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 失败/半态在重新进入编排时统一降为 PENDING，避免 preflight 把恢复后的会话挡在 RETRYING/RUNNING 外。
     */
    private static PipelineExecutionState normalizeCheckpointPipelineState(PipelineExecutionState persisted) {
        if (persisted == null) {
            return PipelineExecutionState.PENDING;
        }
        if (persisted == PipelineExecutionState.FAILED
                || persisted == PipelineExecutionState.RETRYING
                || persisted == PipelineExecutionState.RUNNING
                || persisted == PipelineExecutionState.STEP_FAILED) {
            return PipelineExecutionState.PENDING;
        }
        return persisted;
    }

    /**
     * 每步执行前从库合并全局 + 本书配置，供 LLM 装饰层按 Agent 类型读取。
     */
    private void refreshAgentRuntimeConfig(NovelContext context) {
        INovelAgentRuntimeConfigLoader loader = agentRuntimeConfigLoaderProvider.getIfAvailable();
        if (loader == null || context == null) {
            return;
        }
        try {
            NovelAgentRuntimeConfig cfg = loader.load(context.getNovelId());
            context.setAttribute(NovelContextKeys.AGENT_RUNTIME_CONFIG, cfg);
            log.debug("已刷新 novel_agent_config，novelId={}, agentTypeCount={}",
                    context.getNovelId(), cfg.agentTypeCount());
        } catch (Exception e) {
            log.warn("加载 novel_agent_config 失败，本步将不使用库内 LLM 覆盖: {}", e.getMessage());
        }
    }

    /**
     * 单次执行一步（不重试）；失败抛异常以便外层退避重试，阶段未找到则返回 success=false。
     */
    private StepExecutionResult executeNextStepOnce(NovelContext context) {
        refreshAgentRuntimeConfig(context);

        context.setPipelineExecutionState(PipelineExecutionState.RUNNING);

        String currentStage = context.getCurrentStage();
        log.info("执行下一步，当前阶段: {}, novelId: {}", currentStage, context.getNovelId());

        AbstractExecuteSupport currentNode = getNodeByStage(currentStage);

        if (currentNode == null) {
            log.warn("未找到对应的节点，当前阶段: {}", currentStage);
            String msg = "未找到对应的执行节点";
            context.setPipelineExecutionState(PipelineExecutionState.FAILED);
            context.setAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE, msg);
            return StepExecutionResult.builder()
                    .success(false)
                    .message(msg)
                    .currentStage(currentStage)
                    .pipelineExecutionState(PipelineExecutionState.FAILED)
                    .build();
        }

        log.info("找到执行节点: {}, 开始执行", currentNode.getClass().getSimpleName());

        AbstractExecuteSupport nextNode;
        try {
            nextNode = currentNode.executeStep(context);
        } catch (RuntimeException e) {
            context.setPipelineExecutionState(PipelineExecutionState.STEP_FAILED);
            throw e;
        }

        log.info("节点执行完成，返回的下一个节点: {}", nextNode != null ? nextNode.getClass().getSimpleName() : "null");

        String nextStage = nextNode != null ? getStageByNode(nextNode) : GenerationStage.COMPLETE.getName();

        GenerationStageStateMachine.logIfAtypicalTransition(currentStage, nextStage);

        Object generatedData = getGeneratedDataByStage(context, currentStage);

        if (nextNode != null) {
            context.setCurrentStage(nextStage);
        } else {
            context.setCurrentStage(GenerationStage.COMPLETE.name());
        }

        context.removeAttribute(NovelContextKeys.LAST_STAGE_FAILURE_MESSAGE);
        PipelineExecutionState after = nextNode == null ? PipelineExecutionState.COMPLETED : PipelineExecutionState.STAGE_SUCCEEDED;
        context.setPipelineExecutionState(after);

        log.info("节点执行完成，当前阶段: {}, 下一个阶段: {}, pipelineState: {}", currentStage, nextStage, after);

        return StepExecutionResult.builder()
                .success(true)
                .currentStage(currentStage)
                .nextStage(nextStage)
                .nodeName(currentNode.getClass().getSimpleName())
                .nextNodeName(nextNode != null ? nextNode.getClass().getSimpleName() : null)
                .data(generatedData)
                .isComplete(nextNode == null)
                .pipelineExecutionState(after)
                .build();
    }
    
    /**
     * 根据阶段获取对应的节点
     * 支持枚举名称（SEED）和中文名称（种子阶段）
     */
    private AbstractExecuteSupport getNodeByStage(String stage) {
        if (stage == null || stage.isEmpty()) {
            return rootExecuteNode;
        }
        
        // 先尝试匹配枚举名称
        switch (stage) {
            case "ROOT":
            case "LOAD_DATA":
                return rootExecuteNode;
            case "SEED":
                return seedExecuteNode;
            case "NOVEL_PLAN":
                return planExecuteNode;
            case "VOLUME_PLAN":
                return volumeExecuteNode;
            case "CHAPTER_OUTLINE":
                return chapterExecuteNode;
            case "SCENE_GENERATION":
            case "SCENE":
                return sceneExecuteNode;
            case "VALIDATION":
                return validationExecuteNode;
            case "COMPLETE":
                return null; // 已完成，没有下一个节点
        }
        
        // 如果枚举名称不匹配，尝试匹配中文名称
        switch (stage) {
            case "种子阶段":
                return seedExecuteNode;
            case "小说规划":
                return planExecuteNode;
            case "卷/册规划":
                return volumeExecuteNode;
            case "章节梗概":
                return chapterExecuteNode;
            case "场景生成":
                return sceneExecuteNode;
            case "校验阶段":
                return validationExecuteNode;
            case "完成":
                return null; // 已完成，没有下一个节点
            default:
                log.warn("未知的阶段: {}, 返回root节点", stage);
                return rootExecuteNode;
        }
    }
    
    /**
     * 根据节点获取对应的阶段名称（返回枚举名称）
     */
    private String getStageByNode(AbstractExecuteSupport node) {
        if (node == null) {
            return GenerationStage.COMPLETE.name();
        }
        
        // 直接比较节点实例
        if (node == rootExecuteNode) {
            return "ROOT";
        } else if (node == seedExecuteNode) {
            return GenerationStage.SEED.name();
        } else if (node == planExecuteNode) {
            return GenerationStage.NOVEL_PLAN.name();
        } else if (node == volumeExecuteNode) {
            return GenerationStage.VOLUME_PLAN.name();
        } else if (node == chapterExecuteNode) {
            return GenerationStage.CHAPTER_OUTLINE.name();
        } else if (node == sceneExecuteNode) {
            return GenerationStage.SCENE_GENERATION.name();
        } else if (node == validationExecuteNode) {
            return GenerationStage.VALIDATION.name();
        }
        
        // 如果无法匹配，尝试通过类名匹配
        String nodeName = node.getClass().getSimpleName();
        if (nodeName.contains("Root")) {
            return "ROOT";
        } else if (nodeName.contains("Seed")) {
            return GenerationStage.SEED.name();
        } else if (nodeName.contains("Plan") && !nodeName.contains("Volume")) {
            return GenerationStage.NOVEL_PLAN.name();
        } else if (nodeName.contains("Volume")) {
            return GenerationStage.VOLUME_PLAN.name();
        } else if (nodeName.contains("Chapter")) {
            return GenerationStage.CHAPTER_OUTLINE.name();
        } else if (nodeName.contains("Scene")) {
            return GenerationStage.SCENE_GENERATION.name();
        } else if (nodeName.contains("Validation")) {
            return GenerationStage.VALIDATION.name();
        }
        
        return GenerationStage.COMPLETE.name();
    }
    
    /**
     * 根据阶段获取生成的数据
     * 支持枚举名称和中文名称
     */
    private Object getGeneratedDataByStage(NovelContext context, String stage) {
        if (stage == null) {
            return null;
        }
        
        // 先尝试枚举名称
        switch (stage) {
            case "SEED":
            case "种子阶段":
                return context.getAttribute("seed");
            case "NOVEL_PLAN":
            case "小说规划":
                return context.getAttribute("plan");
            case "VOLUME_PLAN":
            case "卷/册规划":
                return context.getAttribute("currentVolume");
            case "CHAPTER_OUTLINE":
            case "章节梗概":
                return context.getAttribute("currentChapter");
            case "SCENE_GENERATION":
            case "SCENE":
            case "场景生成":
                // 支持两种键名：currentScene 和 scene
                Object scene = context.getAttribute("currentScene");
                return scene != null ? scene : context.getAttribute("scene");
            case "VALIDATION":
            case "校验阶段":
                // 验证阶段返回场景数据（被验证的内容）
                return context.getAttribute("currentScene");
            case "ROOT":
            case "LOAD_DATA":
                return null; // Root节点不生成数据
            default:
                return null;
        }
    }
    
    /**
     * 执行步骤结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepExecutionResult {
        /**
         * 是否成功
         */
        private boolean success;
        
        /**
         * 消息（错误时使用）
         */
        private String message;
        
        /**
         * 当前阶段
         */
        private String currentStage;
        
        /**
         * 下一个阶段
         */
        private String nextStage;
        
        /**
         * 当前节点名称
         */
        private String nodeName;
        
        /**
         * 下一个节点名称
         */
        private String nextNodeName;
        
        /**
         * 生成的数据
         */
        private Object data;
        
        /**
         * 是否完成（没有下一个节点）
         */
        private boolean isComplete;

        /**
         * 本步完成后上下文所处的 Pipeline 生命周期状态（{@link PipelineExecutionState}）
         */
        private PipelineExecutionState pipelineExecutionState;
    }
    
}
