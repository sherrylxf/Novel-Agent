package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.api.INovelAgentService;
import cn.bugstack.novel.api.dto.NovelGenerateRequestDTO;
import cn.bugstack.novel.api.dto.NovelGenerateResponseDTO;
import cn.bugstack.novel.api.dto.NovelPlanSaveRequestDTO;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.ExecutionStateService;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelProject;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.novel.INovelContinuationService;
import cn.bugstack.novel.domain.service.novel.INovelPlanService;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import cn.bugstack.novel.types.enums.GenerationStage;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

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
                        // 首次调用，初始化上下文
                        context = orchestrator.initializeContext(
                                request.getGenre(),
                                request.getCoreConflict(),
                                request.getWorldSetting(),
                                request.getNovelId(),
                                request.getTargetWordCount()
                        );
                        executionStateService.saveContext(request.getSessionId(), context);
                    }
                    
                    // 执行第一步（从当前阶段开始）
                    executeStepByStep(emitter, request.getSessionId(), context, request.getMaxStep());
                    
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
                        executionStateService.saveContext(request.getSessionId(), context);
                    }

                    if (GenerationStage.COMPLETE.name().equals(context.getCurrentStage())) {
                        sendError(emitter, request.getSessionId(), "当前小说已经生成到规划末尾，无法继续创作新章节");
                        return;
                    }

                    String continueMode = request.getContinueMode();
                    if ("step".equalsIgnoreCase(continueMode)) {
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
                        String.format("节点[%s]执行完成", result.getNodeName()));
                
                // 如果已完成，发送完成消息并结束
                if (result.isComplete()) {
                    log.info("流程执行完成，sessionId: {}, 当前阶段: {}", sessionId, result.getCurrentStage());
                    // 发送等待确认消息（让用户查看最终结果）
                    sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(), 
                            result.getNodeName(), result.getData(), result.getNextStage());
                    // 发送完成消息
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    break;
                }
                
                // 发送等待确认消息（包含生成的内容）
                sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(), 
                        result.getNodeName(), result.getData(), result.getNextStage());
                
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
                        sendProgress(emitter, sessionId, result.getCurrentStage(), "用户取消执行");
                        break;
                    }
                    
                    log.info("用户确认继续，sessionId: {}, 下一步: {}", sessionId, result.getNextStage());
                } catch (InterruptedException e) {
                    log.warn("等待用户确认时被中断，sessionId: {}", sessionId);
                    Thread.currentThread().interrupt();
                    sendProgress(emitter, sessionId, result.getCurrentStage(), "执行被中断");
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
                        String.format("节点[%s]执行完成", result.getNodeName()));

                if (result.isComplete()) {
                    NovelPlan plan = context.getAttribute("plan");
                    sendComplete(emitter, sessionId, plan != null ? plan.getNovelId() : context.getNovelId());
                    break;
                }

                if (GenerationStage.VALIDATION.name().equals(result.getCurrentStage())) {
                    executionStateService.setCurrentNode(sessionId, result.getNodeName());
                    sendWaitingForApproval(emitter, sessionId, result.getCurrentStage(),
                            result.getNodeName(), result.getData(), result.getNextStage());
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
    
    /**
     * 发送进度消息
     */
    private void sendProgress(ResponseBodyEmitter emitter, String sessionId, String stage, String content) {
        try {
            cn.bugstack.novel.api.dto.NovelGenerateResponseDTO response = 
                    cn.bugstack.novel.api.dto.NovelGenerateResponseDTO.progress(sessionId, stage, content);
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
                                         String stage, String nodeName, Object data, String nextStage) {
        try {
            NovelGenerateResponseDTO response = 
                    NovelGenerateResponseDTO.waitingForApproval(sessionId, stage, nodeName, data, nextStage);
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
