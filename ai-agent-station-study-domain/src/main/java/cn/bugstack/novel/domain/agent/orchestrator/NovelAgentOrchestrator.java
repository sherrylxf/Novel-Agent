package cn.bugstack.novel.domain.agent.orchestrator;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.agent.service.execute.chain.RootExecuteNode;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

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
                .build();
        
        // 设置参数到上下文
        context.setAttribute("genre", genre);
        context.setAttribute("coreConflict", coreConflict);
        context.setAttribute("worldSetting", worldSetting);
        
        try {
            // 使用责任链模式执行
            rootExecuteNode.execute(context);
            
            // 获取结果
            NovelPlan plan = context.getAttribute("plan");
            
            context.setCurrentStage(GenerationStage.COMPLETE.getName());
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
     */
    public NovelContext initializeContext(String genre, String coreConflict, String worldSetting, 
                                         String novelId, Integer targetWordCount) {
        NovelContext context = NovelContext.builder()
                .novelId(novelId != null ? novelId : "novel-" + System.currentTimeMillis())
                .currentStage(GenerationStage.SEED.name()) // 使用枚举名称，而不是getName()
                .build();
        
        // 设置参数到上下文
        context.setAttribute("genre", genre);
        context.setAttribute("coreConflict", coreConflict);
        context.setAttribute("worldSetting", worldSetting);
        context.setAttribute("targetWordCount", targetWordCount);
        
        log.info("初始化上下文，novelId: {}, stage: {}, targetWordCount: {}", 
                context.getNovelId(), context.getCurrentStage(), targetWordCount);
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
        String currentStage = context.getCurrentStage();
        log.info("执行下一步，当前阶段: {}, novelId: {}", currentStage, context.getNovelId());
        
        try {
            // 根据当前阶段获取下一个节点
            AbstractExecuteSupport currentNode = getNodeByStage(currentStage);
            
            if (currentNode == null) {
                log.warn("未找到对应的节点，当前阶段: {}", currentStage);
                return StepExecutionResult.builder()
                        .success(false)
                        .message("未找到对应的执行节点")
                        .currentStage(currentStage)
                        .build();
            }
            
            log.info("找到执行节点: {}, 开始执行", currentNode.getClass().getSimpleName());
            
            // 执行当前节点（但不自动执行下一个节点）
            AbstractExecuteSupport nextNode = currentNode.executeStep(context);
            
            log.info("节点执行完成，返回的下一个节点: {}", nextNode != null ? nextNode.getClass().getSimpleName() : "null");
            
            // 获取下一个阶段
            String nextStage = nextNode != null ? getStageByNode(nextNode) : GenerationStage.COMPLETE.getName();
            
            // 获取生成的数据
            Object generatedData = getGeneratedDataByStage(context, currentStage);
            
            // 更新当前阶段（使用枚举名称）
            if (nextNode != null) {
                context.setCurrentStage(nextStage);
            } else {
                context.setCurrentStage(GenerationStage.COMPLETE.name());
            }
            
            log.info("节点执行完成，当前阶段: {}, 下一个阶段: {}", currentStage, nextStage);
            
            return StepExecutionResult.builder()
                    .success(true)
                    .currentStage(currentStage)
                    .nextStage(nextStage)
                    .nodeName(currentNode.getClass().getSimpleName())
                    .nextNodeName(nextNode != null ? nextNode.getClass().getSimpleName() : null)
                    .data(generatedData)
                    .isComplete(nextNode == null)
                    .build();
            
        } catch (Exception e) {
            log.error("执行节点失败，当前阶段: {}", currentStage, e);
            return StepExecutionResult.builder()
                    .success(false)
                    .message("执行失败: " + e.getMessage())
                    .currentStage(currentStage)
                    .build();
        }
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
    }
    
}
