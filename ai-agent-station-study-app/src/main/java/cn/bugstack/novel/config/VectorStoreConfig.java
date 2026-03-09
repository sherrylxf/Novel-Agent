package cn.bugstack.novel.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
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
 */
@Slf4j
@Configuration
@ConditionalOnBean(name = "pgVectorJdbcTemplate")
@ConditionalOnProperty(name = "spring.ai.openai.api-key")
public class VectorStoreConfig {
    
    /**
     * 创建 EmbeddingModel Bean
     * 使用 OpenAI 兼容的 API（支持 DeepSeek/Qwen/智谱AI等）
     * 
     * 支持的提供商：
     * - OpenAI: https://api.openai.com/v1, model: text-embedding-3-small
     * - 智谱AI: https://open.bigmodel.cn/api/paas/v4, model: embedding-3
     *   * API路径: POST /paas/v4/embeddings
     *   * 模型选项: embedding-3（默认2048维，支持256/512/1024/2048）或 embedding-2（固定1024维）
     *   * 注意：embedding-3单条请求最多3072 tokens，数组最大64条
     *   *       embedding-2单条请求最多512 tokens，数组总长度不超过8K
     * - Qwen: https://dashscope.aliyuncs.com/compatible-mode/v1, model: text-embedding-v2
     * - DeepSeek: 不支持embeddings API
     * 
     * 注意：可以通过 EMBEDDING_API_KEY 和 EMBEDDING_BASE_URL 环境变量单独配置embeddings API
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.embedding.options.model:embedding-3}") String embeddingModel,
            @Value("${spring.ai.openai.embedding-api-key:}") String configEmbeddingApiKey,
            @Value("${spring.ai.openai.embedding-base-url:}") String configEmbeddingBaseUrl) {
        
        // 优先级：环境变量 > 配置文件 > 默认值（使用Chat API的配置）
        String embeddingApiKey = System.getenv("EMBEDDING_API_KEY");
        String embeddingBaseUrl = System.getenv("EMBEDDING_BASE_URL");
        
        // 如果环境变量未设置，使用配置文件中的值
        if (embeddingApiKey == null || embeddingApiKey.isEmpty()) {
            embeddingApiKey = configEmbeddingApiKey;
        }
        if (embeddingBaseUrl == null || embeddingBaseUrl.isEmpty()) {
            embeddingBaseUrl = configEmbeddingBaseUrl;
        }
        
        // 使用embeddings专用的配置（如果提供），否则使用默认配置
        String finalApiKey = (embeddingApiKey != null && !embeddingApiKey.isEmpty() && !embeddingApiKey.trim().isEmpty()) 
                ? embeddingApiKey : apiKey;
        String finalBaseUrl = (embeddingBaseUrl != null && !embeddingBaseUrl.isEmpty() && !embeddingBaseUrl.trim().isEmpty()) 
                ? embeddingBaseUrl : baseUrl;
        
        // 调试日志：查看配置来源
        log.info("Embeddings配置来源检查:");
        log.info("  - 环境变量 EMBEDDING_API_KEY: {}", System.getenv("EMBEDDING_API_KEY") != null ? "已设置" : "未设置");
        log.info("  - 环境变量 EMBEDDING_BASE_URL: {}", System.getenv("EMBEDDING_BASE_URL") != null ? "已设置" : "未设置");
        log.info("  - 配置文件 embedding-api-key: {}", configEmbeddingApiKey != null && !configEmbeddingApiKey.isEmpty() ? "已设置" : "未设置");
        log.info("  - 配置文件 embedding-base-url: {}", configEmbeddingBaseUrl != null && !configEmbeddingBaseUrl.isEmpty() ? "已设置" : "未设置");
        log.info("  - 最终使用的 API Key来源: {}", finalApiKey.equals(apiKey) ? "默认配置(Chat API)" : "Embeddings专用配置");
        log.info("  - 最终使用的 Base URL来源: {}", finalBaseUrl.equals(baseUrl) ? "默认配置(Chat API)" : "Embeddings专用配置");
        
        // 处理不同提供商的API路径格式
        String embeddingsPath;
        String normalizedBaseUrl;
        
        // 智谱AI特殊处理：https://open.bigmodel.cn/api/paas/v4/embeddings
        if (finalBaseUrl.contains("bigmodel.cn")) {
            // 智谱AI格式：base-url应该包含完整路径到v4
            // 确保baseUrl以斜杠结尾，embeddingsPath不以斜杠开头（相对路径）
            if (finalBaseUrl.endsWith("/v4")) {
                normalizedBaseUrl = finalBaseUrl + "/";
                embeddingsPath = "embeddings";
            } else if (finalBaseUrl.endsWith("/v4/")) {
                normalizedBaseUrl = finalBaseUrl;
                embeddingsPath = "embeddings";
            } else if (finalBaseUrl.contains("/paas/v4")) {
                // 已经是完整路径，确保格式正确
                if (finalBaseUrl.endsWith("/v4")) {
                    normalizedBaseUrl = finalBaseUrl + "/";
                    embeddingsPath = "embeddings";
                } else if (finalBaseUrl.endsWith("/v4/")) {
                    normalizedBaseUrl = finalBaseUrl;
                    embeddingsPath = "embeddings";
                } else {
                    // 如果包含/paas/v4但不以/v4结尾，可能需要调整
                    normalizedBaseUrl = finalBaseUrl.endsWith("/") ? finalBaseUrl + "v4/" : finalBaseUrl + "/v4/";
                    embeddingsPath = "embeddings";
                }
            } else {
                // 自动补全智谱AI路径
                normalizedBaseUrl = finalBaseUrl.endsWith("/") ? finalBaseUrl + "paas/v4/" : finalBaseUrl + "/paas/v4/";
                embeddingsPath = "embeddings";
            }
            log.info("检测到智谱AI配置，自动调整embeddings路径");
        } else if (finalBaseUrl.contains("ollama") || finalBaseUrl.contains("localhost:11434")) {
            // Ollama格式：http://ollama:11434/v1 或 http://localhost:11434/v1
            // 确保baseUrl以/v1/结尾
            if (finalBaseUrl.endsWith("/v1")) {
                normalizedBaseUrl = finalBaseUrl + "/";
                embeddingsPath = "embeddings";
            } else if (finalBaseUrl.endsWith("/v1/")) {
                normalizedBaseUrl = finalBaseUrl;
                embeddingsPath = "embeddings";
            } else {
                // 自动补全Ollama路径
                normalizedBaseUrl = finalBaseUrl.endsWith("/") ? finalBaseUrl + "v1/" : finalBaseUrl + "/v1/";
                embeddingsPath = "embeddings";
            }
            log.info("检测到Ollama配置，自动调整embeddings路径");
        } else if (finalBaseUrl.endsWith("/v1")) {
            // OpenAI标准格式：base-url已经包含/v1，确保以斜杠结尾
            normalizedBaseUrl = finalBaseUrl + "/";
            embeddingsPath = "embeddings";
        } else if (finalBaseUrl.endsWith("/v1/")) {
            // base-url以/v1/结尾
            normalizedBaseUrl = finalBaseUrl;
            embeddingsPath = "embeddings";
        } else {
            // 其他格式，尝试标准路径
            normalizedBaseUrl = finalBaseUrl.endsWith("/") ? finalBaseUrl : finalBaseUrl + "/";
            embeddingsPath = "v1/embeddings";
        }
        
        log.info("配置EmbeddingModel - baseUrl: {}, embeddingsPath: {}, model: {}", 
                normalizedBaseUrl, embeddingsPath, embeddingModel);
        if (!finalApiKey.equals(apiKey) || !finalBaseUrl.equals(baseUrl)) {
            log.info("使用独立的Embeddings API配置（与Chat API分离）");
        }
        
        // 构建完整的请求URL用于日志
        String fullUrl = normalizedBaseUrl.endsWith("/") 
                ? normalizedBaseUrl + embeddingsPath 
                : normalizedBaseUrl + "/" + embeddingsPath;
        log.info("Embeddings API完整URL: {}", fullUrl);
        
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizedBaseUrl)
                .apiKey(finalApiKey)
                .embeddingsPath(embeddingsPath)  // 明确指定embeddings路径
                .build();
        
        // 创建OpenAiEmbeddingModel实例
        OpenAiEmbeddingModel baseEmbeddingModel = new OpenAiEmbeddingModel(openAiApi);
        
        // 创建包装类，在每次调用时自动添加模型名称
        // 这是因为手动创建的 OpenAiEmbeddingModel 不会自动读取配置文件中的模型名称
        EmbeddingModel embeddingModelInstance = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                // 始终使用配置的模型名（如 nomic-embed-text），避免使用请求中自带的 OpenAI 默认 model（如 text-embedding-ada-002）
                try {
                    java.lang.reflect.Field optionsField = EmbeddingRequest.class.getDeclaredField("options");
                    optionsField.setAccessible(true);
                    OpenAiEmbeddingOptions newOptions = OpenAiEmbeddingOptions.builder().build();
                    try {
                        java.lang.reflect.Field modelField = OpenAiEmbeddingOptions.class.getDeclaredField("model");
                        modelField.setAccessible(true);
                        modelField.set(newOptions, embeddingModel);
                        log.debug("通过反射成功设置模型名称: {}", embeddingModel);
                    } catch (Exception e) {
                        log.warn("无法通过反射设置OpenAiEmbeddingOptions的model字段，将使用原始请求: {}", e.getMessage());
                        return baseEmbeddingModel.call(request);
                    }
                    optionsField.set(request, newOptions);
                    log.debug("EmbeddingRequest 使用配置模型: {}", embeddingModel);
                    return baseEmbeddingModel.call(request);
                } catch (Exception ex) {
                    String errorMsg = ex.getMessage();
                    if (errorMsg != null && (errorMsg.contains("429") || errorMsg.contains("余额") || errorMsg.contains("1113"))) {
                        log.warn("API调用失败（可能是账户余额问题）: {}", errorMsg);
                    } else {
                        log.warn("无法通过反射修改EmbeddingRequest的options，将使用原始请求: {}", errorMsg);
                    }
                    return baseEmbeddingModel.call(request);
                }
            }
            
            @Override
            public float[] embed(Document document) {
                // 必须带上配置的模型名，否则会走 OpenAiEmbeddingModel 默认的 text-embedding-ada-002（Ollama 不支持）
                OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().build();
                try {
                    java.lang.reflect.Field modelField = OpenAiEmbeddingOptions.class.getDeclaredField("model");
                    modelField.setAccessible(true);
                    modelField.set(options, embeddingModel);
                } catch (Exception e) {
                    log.warn("embed(Document) 设置模型失败，使用默认: {}", e.getMessage());
                }
                EmbeddingRequest req = new EmbeddingRequest(List.of(document.getText()), options);
                EmbeddingResponse response = baseEmbeddingModel.call(req);
                if (response.getResults() == null || response.getResults().isEmpty()) {
                    return new float[0];
                }
                return response.getResults().get(0).getOutput();
            }
        };
        
        log.info("EmbeddingModel创建完成（带模型名称包装），baseUrl: {}, embeddingsPath: {}, model: {}", 
                normalizedBaseUrl, embeddingsPath, embeddingModel);
        return embeddingModelInstance;
    }
    
    /**
     * 创建 VectorStore Bean
     * 
     * 注意：Spring AI会自动创建表，但需要先能获取embedding dimensions
     * nomic-embed-text模型的向量维度是768
     * 
     * 如果表不存在，Spring AI会在首次使用时自动创建
     * 如果表已存在但维度不匹配，需要手动删除表后重新创建
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
