package cn.bugstack.novel.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量存储配置
 * 
 * 使用PostgreSQL + pgvector
 * 手动创建 EmbeddingModel 和 VectorStore Bean
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Configuration
@ConditionalOnBean(name = "pgVectorJdbcTemplate")
@ConditionalOnProperty(name = "spring.ai.openai.api-key")
public class VectorStoreConfig {
    
    /**
     * 创建 EmbeddingModel Bean
     * 使用 OpenAI 兼容的 API（支持 DeepSeek/Qwen 等）
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        return new OpenAiEmbeddingModel(openAiApi);
    }
    
    /**
     * 创建 VectorStore Bean
     */
    @Bean
    public VectorStore vectorStore(
            @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("novel_vector_store")
                .build();
    }
    
}
