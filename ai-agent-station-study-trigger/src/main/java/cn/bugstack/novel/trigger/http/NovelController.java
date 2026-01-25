package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.api.INovelAgentService;
import cn.bugstack.novel.api.dto.NovelGenerateRequestDTO;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.annotation.Resource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Novel Agent HTTP接口
 * 参考课程第3-12节：Agent服务接口和UI对接
 *
 * @author xiaofuge bugstack.cn @小傅哥
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
    
    @Override
    @PostMapping("/generate")
    public ResponseBodyEmitter generateNovel(@RequestBody NovelGenerateRequestDTO request, HttpServletResponse response) {
        log.info("收到小说生成请求，请求信息：{}", JSON.toJSONString(request));
        
        try {
            // 设置SSE响应头
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            
            // 创建流式输出对象
            ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);
            
            // 异步执行小说生成
            threadPoolExecutor.execute(() -> {
                try {
                    // 发送开始消息
                    sendProgress(emitter, request.getSessionId(), "seed", "开始生成小说Seed...");
                    
                    // 执行小说生成流程
                    NovelPlan plan = orchestrator.generateNovel(
                            request.getGenre(),
                            request.getCoreConflict(),
                            request.getWorldSetting()
                    );
                    
                    // 发送进度消息（实际应该在每个节点执行时发送）
                    sendProgress(emitter, request.getSessionId(), "plan", "小说规划完成");
                    sendProgress(emitter, request.getSessionId(), "volume", "卷规划完成");
                    sendProgress(emitter, request.getSessionId(), "chapter", "章节梗概生成完成");
                    sendProgress(emitter, request.getSessionId(), "scene", "场景生成完成");
                    sendProgress(emitter, request.getSessionId(), "validation", "校验完成");
                    
                    // 发送完成消息
                    sendComplete(emitter, request.getSessionId(), plan.getNovelId());
                    
                } catch (Exception e) {
                    log.error("小说生成异常：{}", e.getMessage(), e);
                    sendError(emitter, request.getSessionId(), "执行异常：" + e.getMessage());
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
            cn.bugstack.novel.api.dto.NovelGenerateResponseDTO response = 
                    cn.bugstack.novel.api.dto.NovelGenerateResponseDTO.error(sessionId, errorMessage);
            String sseData = "data: " + JSON.toJSONString(response) + "\n\n";
            emitter.send(sseData);
        } catch (Exception e) {
            log.error("发送错误消息失败：{}", e.getMessage(), e);
        }
    }
    
}
