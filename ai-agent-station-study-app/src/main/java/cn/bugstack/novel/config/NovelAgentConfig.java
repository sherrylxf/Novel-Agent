package cn.bugstack.novel.config;

import cn.bugstack.novel.domain.agent.impl.generation.SceneGenerationAgent;
import cn.bugstack.novel.domain.agent.impl.planning.ChapterOutlineAgent;
import cn.bugstack.novel.domain.agent.impl.planning.NovelPlannerAgent;
import cn.bugstack.novel.domain.agent.impl.planning.NovelSeedAgent;
import cn.bugstack.novel.domain.agent.impl.planning.VolumePlannerAgent;
import cn.bugstack.novel.domain.agent.impl.validation.ConsistencyGuardAgent;
import cn.bugstack.novel.domain.agent.impl.validation.KGRuleValidatorAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.IRAGService.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Novel Agent配置类
 * 注册所有Agent到Orchestrator
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Configuration
public class NovelAgentConfig {
    
    @Autowired
    private NovelAgentOrchestrator orchestrator;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * 创建占位 RAG 服务 Bean
     * 只有在没有其他 IRAGService bean 时才创建此占位实现
     * VectorRAGService 有 @Primary，如果它存在会被优先使用
     */
    @Bean
    @ConditionalOnMissingBean(IRAGService.class)
    public IRAGService placeholderRAGService() {
        log.warn("RAG服务未配置，创建占位实现Bean");
        return createPlaceholderRAGService();
    }
    
    @PostConstruct
    public void registerAgents() {
        log.info("开始注册Agent...");
        
        // 从 ApplicationContext 获取 RAG 服务，如果不存在则使用占位实现
        // 由于 VectorRAGService 有 @Primary，如果它存在会被优先选择
        IRAGService finalRagService;
        try {
            // 使用 getBeanProvider 延迟获取，避免时序问题
            finalRagService = applicationContext.getBeanProvider(IRAGService.class)
                    .getIfAvailable(() -> {
                        log.warn("未找到 IRAGService bean，使用占位实现");
                        return createPlaceholderRAGService();
                    });
            
            // 检查是否是 VectorRAGService
            if (finalRagService.getClass().getSimpleName().equals("VectorRAGService")) {
                log.info("使用 VectorRAGService（RAG服务已配置）");
            } else {
                log.warn("使用占位 RAG 服务实现: {}", finalRagService.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("无法获取 IRAGService bean，使用占位实现", e);
            finalRagService = createPlaceholderRAGService();
        }
        
        // 从 ApplicationContext 获取 KG 服务，如果不存在则使用占位实现
        IKnowledgeGraphService finalKgService;
        try {
            finalKgService = applicationContext.getBean(IKnowledgeGraphService.class);
        } catch (Exception e) {
            log.warn("无法获取 IKnowledgeGraphService bean，使用占位实现", e);
            finalKgService = createPlaceholderKGService();
        }
        
        // 注册规划层Agent
        orchestrator.registerAgent("NovelSeedAgent", new NovelSeedAgent(finalRagService));
        orchestrator.registerAgent("NovelPlannerAgent", new NovelPlannerAgent());
        orchestrator.registerAgent("VolumePlannerAgent", new VolumePlannerAgent());
        orchestrator.registerAgent("ChapterOutlineAgent", new ChapterOutlineAgent());
        
        // 注册生成执行层Agent
        orchestrator.registerAgent("SceneGenerationAgent", new SceneGenerationAgent(finalRagService));
        
        // 注册约束与审校层Agent
        orchestrator.registerAgent("ConsistencyGuardAgent", new ConsistencyGuardAgent(finalKgService));
        orchestrator.registerAgent("KGRuleValidatorAgent", new KGRuleValidatorAgent(finalKgService));
        
        log.info("Agent注册完成，共注册 {} 个Agent", orchestrator.getAgentCount());
    }
    
    /**
     * 创建占位RAG服务
     */
    private IRAGService createPlaceholderRAGService() {
        log.warn("RAG服务未配置，使用占位实现");
        return new IRAGService() {
            @Override
            public void addDocument(String content, String language, Map<String, Object> metadata) {
                log.debug("RAG服务占位：跳过文档添加");
            }
            
            @Override
            public List<SearchResult> search(String query, String language, int topK) {
                log.debug("RAG服务占位：返回空结果");
                return new ArrayList<>();
            }
        };
    }
    
    /**
     * 创建占位KG服务
     */
    private IKnowledgeGraphService createPlaceholderKGService() {
        log.warn("KG服务未配置，使用占位实现");
        return new IKnowledgeGraphService() {
            @Override
            public void createCharacter(cn.bugstack.novel.domain.model.valobj.Character character) {
                log.debug("KG服务占位：跳过人物创建");
            }
            
            @Override
            public void createRelationship(String fromId, String toId, String relationType, Map<String, Object> properties) {
                log.debug("KG服务占位：跳过关系创建");
            }
            
            @Override
            public List<Map<String, Object>> queryRelationships(String characterId, String relationType) {
                log.debug("KG服务占位：返回空关系列表");
                return new ArrayList<>();
            }
            
            @Override
            public cn.bugstack.novel.domain.model.valobj.Character queryCharacter(String characterId) {
                log.debug("KG服务占位：返回null");
                return null;
            }
            
            @Override
            public void createForeshadowing(String foreshadowingId, String content, Map<String, Object> properties) {
                log.debug("KG服务占位：跳过伏笔创建");
            }
            
            @Override
            public boolean validateRule(String rule) {
                log.debug("KG服务占位：规则校验默认通过");
                return true;
            }
        };
    }
    
}
