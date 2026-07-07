package cn.bugstack.novel.config;

import cn.bugstack.novel.domain.agent.impl.extraction.InfoExtractionAgent;
import cn.bugstack.novel.domain.agent.impl.generation.SceneGenerationAgent;
import cn.bugstack.novel.domain.agent.impl.planning.ChapterOutlineAgent;
import cn.bugstack.novel.domain.agent.impl.planning.NovelPlannerAgent;
import cn.bugstack.novel.domain.agent.impl.planning.NovelSeedAgent;
import cn.bugstack.novel.domain.agent.impl.planning.VolumePlannerAgent;
import cn.bugstack.novel.domain.agent.impl.planning.EndingAgent;
import cn.bugstack.novel.domain.agent.impl.validation.ConsistencyGuardAgent;
import cn.bugstack.novel.domain.agent.impl.validation.KGRuleValidatorAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryContextBuilderService;
import cn.bugstack.novel.domain.service.rag.StoryQueryBuilderService;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.domain.service.novel.INovelWorkspaceService;
import cn.bugstack.novel.domain.service.plot.IPlotTrackerService;
import cn.bugstack.novel.domain.service.prompt.ScenePromptOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Novel Agent配置类
 * 注册所有Agent到Orchestrator
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ScenePromptProperties.class)
public class NovelAgentConfig {

    @Bean
    public ScenePromptOptions scenePromptOptions(ScenePromptProperties properties) {
        if (properties == null) {
            return ScenePromptOptions.defaults();
        }
        return ScenePromptOptions.builder()
                .fewShotEnabled(properties.isFewShotEnabled())
                .maxFewShotChars(properties.getMaxFewShotChars())
                .build();
    }
    
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

    @Bean
    public ApplicationRunner registerAgentsRunner(
            NovelAgentOrchestrator orchestrator,
            IRAGService ragService,
            IKnowledgeGraphService kgService,
            ScenePromptOptions promptOpts,
            org.springframework.beans.factory.ObjectProvider<ILLMClient> llmClientProvider,
            org.springframework.beans.factory.ObjectProvider<IPlotTrackerService> plotTrackerServiceProvider,
            org.springframework.beans.factory.ObjectProvider<StoryQueryBuilderService> storyQueryBuilderServiceProvider,
            org.springframework.beans.factory.ObjectProvider<StoryContextBuilderService> storyContextBuilderServiceProvider,
            org.springframework.beans.factory.ObjectProvider<INovelWorkspaceService> novelWorkspaceServiceProvider) {
        return args -> registerAgents(
                orchestrator,
                ragService,
                kgService,
                promptOpts,
                llmClientProvider.getIfAvailable(),
                plotTrackerServiceProvider.getIfAvailable(),
                storyQueryBuilderServiceProvider.getIfAvailable(),
                storyContextBuilderServiceProvider.getIfAvailable(),
                novelWorkspaceServiceProvider.getIfAvailable());
    }

    private void registerAgents(
            NovelAgentOrchestrator orchestrator,
            IRAGService finalRagService,
            IKnowledgeGraphService finalKgService,
            ScenePromptOptions promptOpts,
            ILLMClient finalLlmClient,
            IPlotTrackerService finalPlotTrackerService,
            StoryQueryBuilderService finalStoryQueryBuilderService,
            StoryContextBuilderService finalStoryContextBuilderService,
            INovelWorkspaceService finalNovelWorkspaceService) {
        log.info("开始注册Agent...");

        String ragName = finalRagService.getClass().getSimpleName();
        if ("VectorRAGService".equals(ragName) || "RedisCachingRAGService".equals(ragName)) {
            log.info("使用 {}（RAG服务已配置）", ragName);
        } else {
            log.warn("使用占位 RAG 服务实现: {}", finalRagService.getClass().getSimpleName());
        }

        if (finalLlmClient != null) {
            log.info("ILLMClient已配置，Agent将使用LLM生成内容");
        } else {
            log.warn("未找到 ILLMClient bean，Agent将使用降级策略");
        }
        
        // 注册规划层Agent
        orchestrator.registerAgent("NovelSeedAgent", 
                new NovelSeedAgent(finalRagService, finalLlmClient));
        orchestrator.registerAgent("NovelPlannerAgent", 
                new NovelPlannerAgent(finalLlmClient));
        orchestrator.registerAgent("VolumePlannerAgent", 
                new VolumePlannerAgent(finalLlmClient));
        orchestrator.registerAgent("ChapterOutlineAgent", 
                new ChapterOutlineAgent(finalLlmClient));
        orchestrator.registerAgent("EndingAgent",
                new EndingAgent(finalLlmClient));
        
        // 注册生成执行层Agent
        orchestrator.registerAgent("SceneGenerationAgent", 
                new SceneGenerationAgent(
                        finalRagService,
                        finalKgService,
                        finalPlotTrackerService,
                        finalLlmClient,
                        finalStoryQueryBuilderService,
                        finalStoryContextBuilderService,
                        finalNovelWorkspaceService,
                        promptOpts));
        orchestrator.registerAgent("InfoExtractionAgent", new InfoExtractionAgent(finalLlmClient));
        
        // 注册约束与审校层Agent
        orchestrator.registerAgent("ConsistencyGuardAgent", 
                new ConsistencyGuardAgent(finalKgService, finalLlmClient));
        orchestrator.registerAgent("KGRuleValidatorAgent", 
                new KGRuleValidatorAgent(finalKgService));
        
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
            public List<IRAGService.SearchResult> search(String query, String language, int topK) {
                log.debug("RAG服务占位：返回空结果");
                return new ArrayList<>();
            }

            @Override
            public IRAGService.DocumentListResult listDocuments(String novelId, String chapterId, int page, int size) {
                IRAGService.DocumentListResult r = new IRAGService.DocumentListResult();
                r.setList(new ArrayList<>());
                r.setTotal(0);
                return r;
            }

            @Override
            public void deleteById(String id) {}

            @Override
            public void deleteByNovelId(String novelId) {}

            @Override
            public List<IRAGService.SearchResult> searchWithMetadataFilter(String query, String language, int topK,
                                                              Map<String, Object> metadataFilter) {
                log.debug("RAG服务占位：返回空结果");
                return new ArrayList<>();
            }
        };
    }
    
}
