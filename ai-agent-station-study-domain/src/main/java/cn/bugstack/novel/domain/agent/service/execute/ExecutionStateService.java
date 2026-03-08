package cn.bugstack.novel.domain.agent.service.execute;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 执行状态管理服务
 * 管理sessionId到context的映射，支持暂停/恢复机制
 * 
 * 实现用户可控制的流程：每个Agent执行后暂停，等待用户确认后再继续
 */
@Slf4j
@Service
public class ExecutionStateService {
    
    /**
     * sessionId -> NovelContext 映射
     */
    private final Map<String, NovelContext> contextMap = new ConcurrentHashMap<>();
    
    /**
     * sessionId -> 等待用户确认的Future
     * 当节点执行完成后，会创建一个Future等待用户确认
     */
    private final Map<String, CompletableFuture<Boolean>> approvalFutures = new ConcurrentHashMap<>();
    
    /**
     * sessionId -> 当前执行节点名称
     */
    private final Map<String, String> currentNodeMap = new ConcurrentHashMap<>();
    
    /**
     * sessionId -> ResponseBodyEmitter（用于发送SSE消息）
     */
    private final Map<String, Object> emitterMap = new ConcurrentHashMap<>();
    
    /**
     * 锁，用于同步操作
     */
    private final Map<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();
    
    /**
     * 保存上下文
     */
    public void saveContext(String sessionId, NovelContext context) {
        contextMap.put(sessionId, context);
        log.debug("保存上下文，sessionId: {}, novelId: {}", sessionId, context.getNovelId());
    }
    
    /**
     * 获取上下文
     */
    public NovelContext getContext(String sessionId) {
        NovelContext context = contextMap.get(sessionId);
        if (context == null) {
            log.warn("未找到上下文，sessionId: {}", sessionId);
        }
        return context;
    }
    
    /**
     * 移除上下文
     */
    public void removeContext(String sessionId) {
        contextMap.remove(sessionId);
        approvalFutures.remove(sessionId);
        currentNodeMap.remove(sessionId);
        emitterMap.remove(sessionId);
        lockMap.remove(sessionId);
        log.debug("移除上下文，sessionId: {}", sessionId);
    }
    
    /**
     * 设置当前执行节点
     */
    public void setCurrentNode(String sessionId, String nodeName) {
        currentNodeMap.put(sessionId, nodeName);
        log.debug("设置当前节点，sessionId: {}, node: {}", sessionId, nodeName);
    }
    
    /**
     * 获取当前执行节点
     */
    public String getCurrentNode(String sessionId) {
        return currentNodeMap.get(sessionId);
    }
    
    /**
     * 保存ResponseBodyEmitter
     */
    public void saveEmitter(String sessionId, Object emitter) {
        emitterMap.put(sessionId, emitter);
        log.debug("保存Emitter，sessionId: {}", sessionId);
    }
    
    /**
     * 获取ResponseBodyEmitter
     */
    @SuppressWarnings("unchecked")
    public <T> T getEmitter(String sessionId) {
        return (T) emitterMap.get(sessionId);
    }
    
    /**
     * 创建等待用户确认的Future
     * 节点执行完成后调用此方法，等待用户确认
     */
    public CompletableFuture<Boolean> createApprovalFuture(String sessionId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        approvalFutures.put(sessionId, future);
        log.info("创建等待确认Future，sessionId: {}", sessionId);
        return future;
    }
    
    /**
     * 用户确认继续执行
     * 返回true表示继续，false表示拒绝/终止
     */
    public void approveAndContinue(String sessionId, boolean approved) {
        CompletableFuture<Boolean> future = approvalFutures.get(sessionId);
        if (future != null) {
            future.complete(approved);
            approvalFutures.remove(sessionId);
            log.info("用户确认，sessionId: {}, approved: {}", sessionId, approved);
        } else {
            log.warn("未找到等待确认的Future，sessionId: {}", sessionId);
        }
    }
    
    /**
     * 获取锁（用于同步操作）
     */
    public ReentrantLock getLock(String sessionId) {
        return lockMap.computeIfAbsent(sessionId, k -> new ReentrantLock());
    }
    
    /**
     * 检查是否有等待确认的Future
     */
    public boolean hasPendingApproval(String sessionId) {
        return approvalFutures.containsKey(sessionId);
    }
    
}
