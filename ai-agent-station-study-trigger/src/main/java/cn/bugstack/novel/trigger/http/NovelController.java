package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.api.INovelAgentService;
import cn.bugstack.novel.api.dto.NovelGenerateRequestDTO;
import cn.bugstack.novel.api.dto.NovelGenerateResponseDTO;
import cn.bugstack.novel.api.dto.NovelPlanSaveRequestDTO;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.pipeline.PipelineCheckpointMergeMode;
import cn.bugstack.novel.domain.agent.service.execute.ExecutionStateService;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelProject;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.model.valobj.NovelContextKeys;
import cn.bugstack.novel.domain.model.valobj.VolumeCompletionSummary;
import cn.bugstack.novel.domain.service.guard.INovelGenerationGuard;
import cn.bugstack.novel.domain.service.novel.INovelContinuationService;
import cn.bugstack.novel.domain.service.novel.INovelPlanService;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import cn.bugstack.novel.types.enums.GenerationStage;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * Novel Agent HTTP接口
 * 参考课程第3-12节：Agent服务接口和UI对接
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/novel")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class NovelController implements INovelAgentService {
    
    @Resource
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    
    @Resource
    private ExecutionStateService executionStateService;
    
    @Resource
    private INovelPlanService novelPlanService;

    @Resource
    private INovelWorkspaceService novelWorkspaceService;

    @Resource
    private INovelContinuationService novelContinuationService;

    @Autowired(required = false)
    private INovelGenerationGuard novelGenerationGuard;
    
    @Override
    @PostMapping("/generate")
    public ResponseBodyEmitter generateNovel(@RequestBody NovelGenerateRequestDTO request, HttpServletResponse response) {
        log.info("收到小说生成请求（分步执行模式），请求信息：{}", JSON.toJSONString(request));
        
        try {
            // 设置SSE响应头
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            if (novelGenerationGuard != null && !novelGenerationGuard.tryAcquireGenerateRequest(request.getSessionId())) {
                ResponseBodyEmitter denied = new ResponseBodyEmitter(Long.MAX_VALUE);
                sendError(denied, request.getSessionId(), "请求过于频繁，请稍后再试");
                denied.complete();
                return denied;
            }
            
            // 创建流式输出对象
            ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);
            
            // 保存emitter到状态服务
            executionStateService.saveEmitter(request.getSessionId(), emitter);
            
            // 异步执行小说生成（分步执行）
            threadPoolExecutor.execute(() -> {
                try {
                    // 初始化或获取上下文
                    NovelContext context = executionStateService.getContext(request.getSessionId());
                    if (context == null) {
                        context = orchestrator.initializeContext(
                                request.getGenre(),
                                request.getCoreConflict(),
                                request.getWorldSetting(),
                                request.getNovelId(),
                                request.getTargetWordCount(),
                                request.getChaptersTotal(),
                                request.getWordsPerChapter()
                        );
                        orchestrator.mergePersistedCheckpoint(context, PipelineCheckpointMergeMode.FRESH_SESSION);
                        executionStateService.saveContext(request.getSessionId(), context);
                    }
                    attachPipelineSession(context, request.getSessionId());
                    applyGenerationRequestToContext(context, request);

                    String generateMode = request.getGenerateMode();
                    if ("full".equalsIgnoreCase(generateMode)) {
                        executeFullNovel(emitter, request.getSessionId(), context, request);
                    } else if ("volume".equalsIgnoreCase(generateMode)) {
                        executeVolumeByVolume(emitter, request.getSessionId(), context, request);
                    } else {
                        executeStepByStep(emitter, request.getSessionId(), context, request.getMaxStep());
                    }
                    
                } catch (Exception e) {
                    log.error("小说生成异常：{}", e.getMessage(), e);
                    sendError(emitter, request.getSessionId(), "执行异常：" + e.getMessage());
                    executionStateService.removeContext(request.getSessionId());
                } finally {
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成流式输出失败：{}", e.getMessage(), e);
                    }
                }
            });
            
            return emitter;
            
        } catch (Exception e) {
            log.error("请求处理异常：{}", e.getMessage(), e);
            ResponseBodyEmitter errorEmitter = new ResponseBodyEmitter();
            try {
                sendError(errorEmitter, request.getSessionId(), "请求处理异常：" + e.getMessage());
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("发送错误信息失败：{}", ex.getMessage(), ex);
            }
            return errorEmitter;
        }
    }

    @PostMapping("/continue-chapter")
    public ResponseBodyEmitter continueChapter(@RequestBody NovelGenerateRequestDTO request, HttpServletResponse response) {
        log.info("收到继续创作下一章请求，novelId: {}, sessionId: {}, mode: {}",
                request.getNovelId(), request.getSessionId(), request.getContinueMode());

        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);
            executionStateService.saveEmitter(request.getSessionId(), emitter);

            threadPoolExecutor.execute(() -> {
                try {
                    NovelContext context = executionStateService.getContext(request.getSessionId());
                    if (context == null) {
                        context = novelContinuationService.buildResumeContext(request.getNovelId());
                        orchestrator.mergePersistedCheckpoint(context, PipelineCheckpointMergeMode.AFTER_CONTINUATION);
                        executionStateService.saveContext(request.getSessionId(), context);
                    }
                    attachPipelineSession(context, request.getSessionId());

                    if (GenerationStage.COMPLETE.name().equals(context.getCurrentStage())) {
                        sendError(emitter, request.getSessionId(), "当前小说已经生成到规划末尾，无法继续创作新章节");
                        return;
                    }

                    String continueMode = request.getContinueMode();
                    Integer chaptersToCreate = request.getChaptersToCreate();
                    Integer wordsPerChapter = request.getWordsPerChapter();
                    if ("batch".equalsIgnoreCase(continueMode) && chaptersToCreate != null && chaptersToCreate > 0) {
                        int wpc = (wordsPerChapter != null && wordsPerChapter > 0) ? wordsPerChapter : 3000;
                        context.setAttribute("wordsPerChapter", wpc);
                        executeBatchChapters(emitter, request.getSessionId(), context, chaptersToCreate);
                    } else if ("step".equalsIgnoreCase(continueMode)) {
                        executeStepByStep(emitter, request.getSessionId(), context, request.getMaxStep());
                    } else {
                        executeUntilChapterReview(emitter, request.getSessionId(), context);
                    }
                } catch (Exception e) {
                    log.error("继续创作下一章异常，novelId: {}", request.getNovelId(), e);
                    sendError(emitter, request.getSessionId(), "继续创作异常：" + e.getMessage());
                } finally {
                    try {
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成继续创作流式输出失败：{}", e.getMessage(), e);
                    }
                }
            });

            return emitter;
        } catch (Exception e) {
            log.error("处理继续创作请求异常：{}", e.getMessage(), e);
            ResponseBodyEmitter errorEmitter = new ResponseBodyEmitter();
            try {
                sendError(errorEmitter, request.getSessionId(), "请求处理异常：" + e.getMessage());
                errorEmitter.complete();
            } catch (Exception ex) {
                log.error("发送错误信息失败：{}", ex.getMessage(), ex);
            }
            return errorEmitter;
        }
    }
    
    private static void applyGenerationRequestToContext(NovelContext context, NovelGenerateRequestDTO request) {
        if (context == null || request == null) {
            return;
        }
        if (request.getMasterPrompt() != null && !request.getMasterPrompt().isBlank()) {
            context.setAttribute(NovelContextKeys.MASTER_PROMPT, request.getMasterPrompt().trim());
        }
        if (request.getTotalVolumes() != null && request.getTotalVolumes() > 0) {
            context.setAttribute(NovelContextKeys.TOTAL_VOLUMES, request.getTotalVolumes());
        }
        if (request.getVolumeTargetWordCount() != null && request.getVolumeTargetWordCount() > 0) {
            context.setAttribute(NovelContextKeys.VOLUME_TARGET_WORD_COUNT, request.getVolumeTargetWordCount());
        }
        if (request.getWordCountTolerance() != null && request.getWordCountTolerance() > 0) {
            context.setAttribute(NovelContextKeys.WORD_COUNT_TOLERANCE, request.getWordCountTolerance());
        }
    }

    /**
     * 按卷自动生成：卷内连续执行不等待确认，每卷结束后推送字数统计并等待用户确认。
     */
    private void executeVolumeByVolume(ResponseBodyEmitter emitter, String sessionId, NovelContext context, NovelGenerateRequestDTO request) {
        java.util.concurrent.locks.ReentrantLock lock = executionStateService.getLock(sessionId);
        try {
            lock.lock();
            while (true) {
                boolean stopInner = false;
                boolean bookComplete = false;
                while (!stopInner) {
                    NovelAgentOrchestrator.StepExecutionResult result = orchestrator.executeNextStep(context);
                    if (!result.isSuccess()) {
                        sendError(emitter, sessionId, result.getMessage());
                        return;
                    }

                    sendProgress(emitter, sessionId, result.getCurrentStage(),
                            String.format("节点[%s]执行完成", result.getNodeName()),
                            pipelineExecutionStateName(result));

                    if (GenerationStage.VALIDATION.name().equals(result.getCurrentStage())) {
                        notifyChapterCompleted(emitter, sessionId, context);
                        maybeSendVolumeCompleted(emitter, sessionId, context);
                        if (context.getAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION) != null) {
                            stopInner = true;
                        }
                    }

                    if (result.isComplete()) {
                        bookComplete = true;
                        stopInner = true;
                    }
                }

                VolumeCompletionSummary summary = context.getAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION);
                if (summary != null) {
                    context.removeAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION);
                    sendWaitingForApproval(emitter, sessionId, GenerationStage.VALIDATION.name(),
                            "VolumeCheckpoint",
                            summary,
                            bookComplete ? GenerationStage.COMPLETE.name() : GenerationStage.VOLUME_PLAN.name(),
                            pipelineExecutionStateName(context));

                    lock.unlock();
                    CompletableFuture<Boolean> approvalFuture = executionStateService.createApprovalFuture(sessionId);
                    executionStateService.setCurrentNode(sessionId, "VolumeCheckpoint");
                    try {
                        Boolean approved = approvalFuture.get();
                        if (!approved) {
                            sendProgress(emitter, sessionId, context.getCurrentStage(), "用户取消继续",
                                    pipelineExecutionStateName(context));
                            return;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Exception e) {
                        sendError(emitter, sessionId, "等待卷确认异常：" + e.getMessage());
                        return;
                    } finally {
                        lock.lock();
                    }
                }

                if (bookComplete || GenerationStage.COMPLETE.name().equals(context.getCurrentStage())) {
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    return;
                }
            }
        } catch (Exception e) {
            log.error("按卷生成异常，sessionId: {}", sessionId, e);
            sendError(emitter, sessionId, "按卷生成异常：" + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 一键生成整本：不等待用户确认，持续执行直到完成
     */
    private void executeFullNovel(ResponseBodyEmitter emitter, String sessionId, NovelContext context, NovelGenerateRequestDTO request) {
        try {
            while (true) {
                NovelAgentOrchestrator.StepExecutionResult result = orchestrator.executeNextStep(context);

                if (!result.isSuccess()) {
                    sendError(emitter, sessionId, result.getMessage());
                    break;
                }

                sendProgress(emitter, sessionId, result.getCurrentStage(),
                        String.format("节点[%s]执行完成", result.getNodeName()),
                        pipelineExecutionStateName(result));

                if (GenerationStage.VALIDATION.name().equals(result.getCurrentStage())) {
                    notifyChapterCompleted(emitter, sessionId, context);
                    maybeSendVolumeCompleted(emitter, sessionId, context);
                }

                if (result.isComplete()) {
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    break;
                }

                sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(),
                        result.getNodeName(), result.getData(), result.getNextStage(),
                        pipelineExecutionStateName(result));
            }
        } catch (Exception e) {
            log.error("一键生成整本异常，sessionId: {}", sessionId, e);
            sendError(emitter, sessionId, "生成异常：" + e.getMessage());
        }
    }

    private void notifyChapterCompleted(ResponseBodyEmitter emitter, String sessionId, NovelContext context) {
        VolumePlan volumePlan = context.getAttribute("currentVolume");
        ChapterOutline outline = context.getAttribute("currentChapter");
        Scene scene = context.getAttribute("currentScene");
        if (outline != null && scene != null) {
            sendChapterCompleted(emitter, sessionId,
                    context.getNovelId(),
                    volumePlan != null ? volumePlan.getVolumeNumber() : 1,
                    outline.getChapterNumber(),
                    outline.getChapterTitle(),
                    scene.getWordCount() != null ? scene.getWordCount() : scene.getContent() != null ? scene.getContent().length() : 0);
        }
    }

    private void maybeSendVolumeCompleted(ResponseBodyEmitter emitter, String sessionId, NovelContext context) {
        VolumeCompletionSummary summary = context.getAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION);
        if (summary == null) {
            return;
        }
        sendVolumeCompleted(emitter, sessionId, context.getNovelId(), summary);
    }

    private void sendChapterCompleted(ResponseBodyEmitter emitter, String sessionId, String novelId,
                                      Integer volumeNumber, Integer chapterNumber, String chapterTitle, Integer wordCount) {
        try {
            NovelGenerateResponseDTO response = NovelGenerateResponseDTO.chapterCompleted(
                    sessionId, novelId, volumeNumber, chapterNumber, chapterTitle, wordCount);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送章节完成消息失败：{}", e.getMessage(), e);
        }
    }

    private void sendVolumeCompleted(ResponseBodyEmitter emitter, String sessionId, String novelId, VolumeCompletionSummary summary) {
        try {
            NovelGenerateResponseDTO response = NovelGenerateResponseDTO.volumeCompleted(sessionId, novelId, summary);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
            log.info("发送卷完成消息，sessionId: {}, volume: {}, volumeWords: {}, bookWords: {}",
                    sessionId, summary.getVolumeNumber(), summary.getVolumeWordCount(), summary.getBookWordCount());
        } catch (Exception e) {
            log.error("发送卷完成消息失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 分步执行（每次执行一个节点，等待用户确认）
     * 使用锁机制防止并发执行
     */
    private void executeStepByStep(ResponseBodyEmitter emitter, String sessionId, NovelContext context, Integer maxStep) {
        int stepCount = 0;
        int maxSteps = (maxStep != null && maxStep > 0) ? maxStep : Integer.MAX_VALUE;
        
        // 获取锁，防止并发执行
        java.util.concurrent.locks.ReentrantLock lock = executionStateService.getLock(sessionId);
        
        try {
            lock.lock(); // 获取锁
            
            while (stepCount < maxSteps) {
                log.info("开始执行第 {} 步，当前阶段: {}, maxSteps: {}", stepCount + 1, context.getCurrentStage(), maxSteps);
                
                // 执行下一步
                NovelAgentOrchestrator.StepExecutionResult result = orchestrator.executeNextStep(context);
                
                if (!result.isSuccess()) {
                    sendError(emitter, sessionId, result.getMessage());
                    break;
                }
                
                stepCount++;
                
                // 发送进度消息
                sendProgress(emitter, sessionId, result.getCurrentStage(),
                        String.format("节点[%s]执行完成", result.getNodeName()),
                        pipelineExecutionStateName(result));

                if (GenerationStage.VALIDATION.name().equals(result.getCurrentStage())) {
                    notifyChapterCompleted(emitter, sessionId, context);
                    maybeSendVolumeCompleted(emitter, sessionId, context);
                }
                
                // 如果已完成，发送完成消息并结束
                if (result.isComplete()) {
                    log.info("流程执行完成，sessionId: {}, 当前阶段: {}", sessionId, result.getCurrentStage());
                    // 发送等待确认消息（让用户查看最终结果）
                    sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(),
                            result.getNodeName(), result.getData(), result.getNextStage(),
                            pipelineExecutionStateName(result));
                    // 发送完成消息
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    break;
                }
                
                Object approvalData = result.getData();
                VolumeCompletionSummary volumeSummary = context.getAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION);
                if (volumeSummary != null) {
                    approvalData = volumeSummary;
                    context.removeAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION);
                }
                // 发送等待确认消息（包含生成的内容）
                sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(),
                        volumeSummary != null ? "VolumeCheckpoint" : result.getNodeName(),
                        approvalData, result.getNextStage(),
                        pipelineExecutionStateName(result));
                
                // 等待用户确认
                CompletableFuture<Boolean> approvalFuture = executionStateService.createApprovalFuture(sessionId);
                executionStateService.setCurrentNode(sessionId, result.getNodeName());
                
                // 释放锁，允许用户确认操作
                lock.unlock();
                
                try {
                    // 等待用户确认（最多等待30分钟）
                    Boolean approved = approvalFuture.get();
                    
                    if (!approved) {
                        log.info("用户拒绝继续，sessionId: {}", sessionId);
                        sendProgress(emitter, sessionId, result.getCurrentStage(), "用户取消执行",
                                pipelineExecutionStateName(context));
                        break;
                    }
                    
                    log.info("用户确认继续，sessionId: {}, 下一步: {}", sessionId, result.getNextStage());
                } catch (InterruptedException e) {
                    log.warn("等待用户确认时被中断，sessionId: {}", sessionId);
                    Thread.currentThread().interrupt();
                    sendProgress(emitter, sessionId, result.getCurrentStage(), "执行被中断",
                            pipelineExecutionStateName(context));
                    break;
                } catch (Exception e) {
                    log.error("等待用户确认时出现异常，sessionId: {}", sessionId, e);
                    sendError(emitter, sessionId, "等待用户确认异常：" + e.getMessage());
                    break;
                } finally {
                    // 重新获取锁，继续执行下一步
                    lock.lock();
                    log.info("重新获取锁成功，准备继续执行下一步，sessionId: {}, stepCount: {}, maxSteps: {}", 
                            sessionId, stepCount, maxSteps);
                }
            }
            
            log.info("分步执行循环结束，sessionId: {}, stepCount: {}, maxSteps: {}", sessionId, stepCount, maxSteps);
            
        } catch (Exception e) {
            log.error("分步执行异常，sessionId: {}", sessionId, e);
            sendError(emitter, sessionId, "执行异常：" + e.getMessage());
        } finally {
            // 确保释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 批量自动完成多章：不等待用户确认，持续执行直到完成指定数量的章节或规划末尾。
     */
    private void executeBatchChapters(ResponseBodyEmitter emitter, String sessionId, NovelContext context, int chaptersToCreate) {
        java.util.concurrent.locks.ReentrantLock lock = executionStateService.getLock(sessionId);
        try {
            lock.lock();
            int chaptersCreated = 0;
            while (chaptersCreated < chaptersToCreate) {
                NovelAgentOrchestrator.StepExecutionResult result = orchestrator.executeNextStep(context);
                if (!result.isSuccess()) {
                    sendError(emitter, sessionId, result.getMessage());
                    break;
                }

                sendProgress(emitter, sessionId, result.getCurrentStage(),
                        String.format("节点[%s]执行完成", result.getNodeName()),
                        pipelineExecutionStateName(result));

                if (result.isComplete()) {
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    break;
                }

                if (GenerationStage.VALIDATION.name().equals(result.getCurrentStage())) {
                    VolumePlan volumePlan = context.getAttribute("currentVolume");
                    ChapterOutline outline = context.getAttribute("currentChapter");
                    Scene scene = context.getAttribute("currentScene");
                    if (outline != null && scene != null) {
                        sendChapterCompleted(emitter, sessionId,
                                context.getNovelId(),
                                volumePlan != null ? volumePlan.getVolumeNumber() : 1,
                                outline.getChapterNumber(),
                                outline.getChapterTitle(),
                                scene.getWordCount() != null ? scene.getWordCount() : scene.getContent() != null ? scene.getContent().length() : 0);
                    }
                    chaptersCreated++;
                    if (chaptersCreated >= chaptersToCreate) {
                        NovelPlan plan = context.getAttribute("plan");
                        sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量自动完成异常，sessionId: {}", sessionId, e);
            sendError(emitter, sessionId, "批量生成异常：" + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 自动继续生成，直到一章完成后的校验节点为止。
     * 这样用户只需要在章节管理页修改确认一次，而不是为章节内每个子节点逐步确认。
     */
    private void executeUntilChapterReview(ResponseBodyEmitter emitter, String sessionId, NovelContext context) {
        java.util.concurrent.locks.ReentrantLock lock = executionStateService.getLock(sessionId);
        try {
            lock.lock();
            while (true) {
                NovelAgentOrchestrator.StepExecutionResult result = orchestrator.executeNextStep(context);
                if (!result.isSuccess()) {
                    sendError(emitter, sessionId, result.getMessage());
                    break;
                }

                sendProgress(emitter, sessionId, result.getCurrentStage(),
                        String.format("节点[%s]执行完成", result.getNodeName()),
                        pipelineExecutionStateName(result));

                if (result.isComplete()) {
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    break;
                }

                if (GenerationStage.VALIDATION.name().equals(result.getCurrentStage())) {
                    executionStateService.setCurrentNode(sessionId, result.getNodeName());
                    sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(),
                            result.getNodeName(), result.getData(), result.getNextStage(),
                            pipelineExecutionStateName(result));
                    break;
                }
            }
        } catch (Exception e) {
            log.error("自动继续生成异常，sessionId: {}", sessionId, e);
            sendError(emitter, sessionId, "继续生成异常：" + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 用户确认继续执行
     * 注意：这里只需要通知等待的Future，不需要重新启动执行流程
     * executeStepByStep已经在等待approvalFuture.get()，通知后会继续执行
     */
    /**
     * 阶段失败（Pipeline FAILED）后，同会话内将状态清为 PENDING，保留当前业务阶段，便于再次触发 generate 而无需全量重跑。
     */
    @PostMapping("/pipeline/reset-failed")
    public NovelGenerateResponseDTO resetPipelineFailed(@RequestParam("sessionId") String sessionId) {
        NovelContext ctx = executionStateService.getContext(sessionId);
        if (ctx == null) {
            return NovelGenerateResponseDTO.error(sessionId, "会话不存在或已过期");
        }
        if (!orchestrator.resetFailedPipelineToRetryable(ctx)) {
            return NovelGenerateResponseDTO.error(sessionId, "当前不是 FAILED 状态，无需重置");
        }
        return NovelGenerateResponseDTO.progress(sessionId, ctx.getCurrentStage(),
                "已重置为可重试（同阶段，仍走指数退避）",
                ctx.getPipelineExecutionState() != null ? ctx.getPipelineExecutionState().name() : null);
    }

    @PostMapping("/approve")
    public cn.bugstack.novel.api.dto.NovelGenerateResponseDTO approveAndContinue(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "approved", defaultValue = "true") boolean approved) {
        
        log.info("收到用户确认请求，sessionId: {}, approved: {}", sessionId, approved);
        
        try {
            // 通知状态服务用户已确认（这会唤醒等待的Future）
            executionStateService.approveAndContinue(sessionId, approved);
            
            // 不需要重新启动执行流程，executeStepByStep已经在等待，通知后会继续执行
            
            return NovelGenerateResponseDTO.progress(sessionId, "approval", 
                    approved ? "已确认，继续执行" : "已拒绝，停止执行");
            
        } catch (Exception e) {
            log.error("处理用户确认异常，sessionId: {}", sessionId, e);
            return NovelGenerateResponseDTO.error(sessionId, "处理确认请求异常：" + e.getMessage());
        }
    }
    
    private static void attachPipelineSession(NovelContext context, String sessionId) {
        if (context != null && sessionId != null) {
            context.setAttribute(NovelContextKeys.SESSION_ID, sessionId);
        }
    }

    private static String pipelineExecutionStateName(NovelAgentOrchestrator.StepExecutionResult result) {
        if (result == null || result.getPipelineExecutionState() == null) {
            return null;
        }
        return result.getPipelineExecutionState().name();
    }

    private static String pipelineExecutionStateName(NovelContext context) {
        if (context == null || context.getPipelineExecutionState() == null) {
            return null;
        }
        return context.getPipelineExecutionState().name();
    }

    /**
     * 发送进度消息
     */
    private void sendProgress(ResponseBodyEmitter emitter, String sessionId, String stage, String content,
                              String pipelineExecutionState) {
        try {
            NovelGenerateResponseDTO response =
                    NovelGenerateResponseDTO.progress(sessionId, stage, content, pipelineExecutionState);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送进度消息失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送完成消息
     */
    private void sendComplete(ResponseBodyEmitter emitter, String sessionId, String novelId) {
        try {
            cn.bugstack.novel.api.dto.NovelGenerateResponseDTO response = 
                    cn.bugstack.novel.api.dto.NovelGenerateResponseDTO.complete(sessionId, novelId);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送完成消息失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送错误消息
     */
    private void sendError(ResponseBodyEmitter emitter, String sessionId, String errorMessage) {
        try {
            NovelGenerateResponseDTO response = 
                    NovelGenerateResponseDTO.error(sessionId, errorMessage);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送错误消息失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送等待用户确认消息
     */
    private void sendWaitingForApproval(ResponseBodyEmitter emitter, String sessionId,
                                        String stage, String nodeName, Object data, String nextStage,
                                        String pipelineExecutionState) {
        try {
            NovelGenerateResponseDTO response =
                    NovelGenerateResponseDTO.waitingForApproval(sessionId, stage, nodeName, data, nextStage,
                            pipelineExecutionState);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
            log.info("发送等待确认消息，sessionId: {}, stage: {}, nodeName: {}", sessionId, stage, nodeName);
        } catch (Exception e) {
            log.error("发送等待确认消息失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 保存小说规划
     */
    @PostMapping("/plan/save")
    public Map<String, Object> saveNovelPlan(@RequestBody NovelPlanSaveRequestDTO request) {
        log.info("收到保存小说规划请求，planId: {}, novelId: {}", request.getPlanId(), request.getNovelId());
        
        try {
            // 参数校验
            if (request.getPlanId() == null || request.getPlanId().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "planId不能为空");
                return result;
            }
            if (request.getNovelId() == null || request.getNovelId().isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "novelId不能为空");
                return result;
            }
            
            log.info("开始转换请求数据，planId: {}, novelId: {}", request.getPlanId(), request.getNovelId());
            
            // 转换为领域实体
            NovelPlan novelPlan = convertToNovelPlan(request);
            log.info("转换完成，planId: {}, novelId: {}, totalVolumes: {}, volumePlans size: {}", 
                    novelPlan.getPlanId(), novelPlan.getNovelId(), 
                    novelPlan.getTotalVolumes(), 
                    novelPlan.getVolumePlans() != null ? novelPlan.getVolumePlans().size() : 0);
            
            // 保存规划
            novelPlanService.saveNovelPlan(novelPlan);
            novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                    .novelId(request.getNovelId())
                    .status(1)
                    .build());
            
            log.info("保存小说规划成功，planId: {}, novelId: {}", request.getPlanId(), request.getNovelId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "保存成功");
            result.put("planId", request.getPlanId());
            result.put("novelId", request.getNovelId());
            return result;
            
        } catch (Exception e) {
            log.error("保存小说规划异常，planId: {}, novelId: {}", request.getPlanId(), request.getNovelId(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "保存失败：" + e.getMessage());
            result.put("error", e.getClass().getName());
            if (e.getCause() != null) {
                result.put("cause", e.getCause().getMessage());
            }
            return result;
        }
    }
    
    /**
     * 查询小说规划（根据规划ID）
     */
    @GetMapping("/plan/{planId}")
    public Map<String, Object> queryPlanByPlanId(@PathVariable("planId") String planId) {
        log.info("查询小说规划，planId: {}", planId);
        
        try {
            NovelPlan novelPlan = novelPlanService.queryByPlanId(planId);
            
            Map<String, Object> result = new HashMap<>();
            if (novelPlan == null) {
                result.put("success", false);
                result.put("message", "规划不存在");
            } else {
                result.put("success", true);
                result.put("data", convertToDTO(novelPlan));
            }
            return result;
            
        } catch (Exception e) {
            log.error("查询小说规划异常：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
            return result;
        }
    }
    
    /**
     * 查询小说规划（根据小说ID）
     */
    @GetMapping("/plan/novel/{novelId}")
    public Map<String, Object> queryPlanByNovelId(@PathVariable("novelId") String novelId) {
        log.info("查询小说规划，novelId: {}", novelId);
        
        try {
            NovelPlan novelPlan = novelPlanService.queryByNovelId(novelId);
            
            Map<String, Object> result = new HashMap<>();
            if (novelPlan == null) {
                result.put("success", false);
                result.put("message", "规划不存在");
            } else {
                result.put("success", true);
                result.put("data", convertToDTO(novelPlan));
            }
            return result;
            
        } catch (Exception e) {
            log.error("查询小说规划异常：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
            return result;
        }
    }
    
    /**
     * 更新小说规划
     */
    @PostMapping("/plan/update")
    public Map<String, Object> updateNovelPlan(@RequestBody NovelPlanSaveRequestDTO request) {
        log.info("收到更新小说规划请求，planId: {}, novelId: {}", request.getPlanId(), request.getNovelId());
        
        try {
            // 转换为领域实体
            NovelPlan novelPlan = convertToNovelPlan(request);
            
            // 更新规划
            novelPlanService.updateNovelPlan(novelPlan);
            novelWorkspaceService.saveOrUpdateNovel(NovelProject.builder()
                    .novelId(request.getNovelId())
                    .status(1)
                    .build());
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("planId", request.getPlanId());
            result.put("novelId", request.getNovelId());
            return result;
            
        } catch (Exception e) {
            log.error("更新小说规划异常：{}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
            return result;
        }
    }
    
    /**
     * 将请求DTO转换为领域实体
     */
    private NovelPlan convertToNovelPlan(NovelPlanSaveRequestDTO request) {
        List<VolumePlan> volumePlans = null;
        if (request.getVolumePlans() != null && !request.getVolumePlans().isEmpty()) {
            volumePlans = request.getVolumePlans().stream()
                    .map(dto -> VolumePlan.builder()
                            .volumeId(dto.getVolumeId())
                            .novelId(request.getNovelId())
                            .volumeNumber(dto.getVolumeNumber())
                            .volumeTitle(dto.getVolumeTitle())
                            .volumeTheme(dto.getVolumeTheme())
                            .chapterCount(dto.getChapterCount())
                            .build())
                    .collect(Collectors.toList());
        }
        
        return NovelPlan.builder()
                .planId(request.getPlanId())
                .novelId(request.getNovelId())
                .totalVolumes(request.getTotalVolumes())
                .chaptersPerVolume(request.getChaptersPerVolume())
                .overallOutline(request.getOverallOutline())
                .volumePlans(volumePlans)
                .build();
    }
    
    /**
     * 将领域实体转换为DTO
     */
    private Map<String, Object> convertToDTO(NovelPlan novelPlan) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("planId", novelPlan.getPlanId());
        dto.put("novelId", novelPlan.getNovelId());
        dto.put("totalVolumes", novelPlan.getTotalVolumes());
        dto.put("chaptersPerVolume", novelPlan.getChaptersPerVolume());
        dto.put("overallOutline", novelPlan.getOverallOutline());
        
        if (novelPlan.getVolumePlans() != null && !novelPlan.getVolumePlans().isEmpty()) {
            List<Map<String, Object>> volumePlanDTOs = novelPlan.getVolumePlans().stream()
                    .map(vp -> {
                        Map<String, Object> vpDTO = new HashMap<>();
                        vpDTO.put("volumeId", vp.getVolumeId());
                        vpDTO.put("volumeNumber", vp.getVolumeNumber());
                        vpDTO.put("volumeTitle", vp.getVolumeTitle());
                        vpDTO.put("volumeTheme", vp.getVolumeTheme());
                        vpDTO.put("chapterCount", vp.getChapterCount());
                        return vpDTO;
                    })
                    .collect(Collectors.toList());
            dto.put("volumePlans", volumePlanDTOs);
        }
        
        return dto;
    }
    
}
