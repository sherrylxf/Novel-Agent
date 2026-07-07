package cn.bugstack.novel.config;

import cn.bugstack.novel.domain.agent.service.execute.StageExecutionRetryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 小说生成流水线：阶段重试等可配置项
 */
@Configuration
@EnableConfigurationProperties(NovelPipelineProperties.class)
public class NovelPipelineConfiguration {

    @Bean
    @ConditionalOnMissingBean(StageExecutionRetryPolicy.class)
    public StageExecutionRetryPolicy stageExecutionRetryPolicy(NovelPipelineProperties properties) {
        NovelPipelineProperties.Retry r = properties.getRetry();
        return StageExecutionRetryPolicy.builder()
                .maxAttempts(r.getMaxAttempts())
                .initialDelayMs(r.getInitialDelayMs())
                .maxDelayMs(r.getMaxDelayMs())
                .multiplier(r.getMultiplier())
                .jitterRatio(r.getJitterRatio())
                .build();
    }
}
