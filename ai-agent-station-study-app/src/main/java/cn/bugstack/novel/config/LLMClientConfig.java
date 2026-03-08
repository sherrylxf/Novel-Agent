package cn.bugstack.novel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM客户端配置类
 * 创建统一的ChatModel bean，支持调用DeepSeek/Qwen等国内API
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.openai", name = "api-key")
public class LLMClientConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    /**
     * 创建OpenAiApi实例
     * 注意：虽然类名是OpenAiApi，但可以兼容任何符合OpenAI API格式的提供商（如DeepSeek、Qwen等）
     * 
     * DeepSeek API端点说明：
     * - base_url: https://api.deepseek.com
     * - chat completions: /chat/completions (不带v1前缀)
     * - 如果使用 https://api.deepseek.com/v1，则路径为 /chat/completions
     */
    @Bean
    public OpenAiApi openAiApi() {
        log.info("初始化OpenAiApi - baseUrl: {}, model: {}", baseUrl, model);
        
        // 根据DeepSeek官方文档，chat completions路径是 /chat/completions（不带v1）
        // 但如果baseUrl包含/v1，则路径应该是 chat/completions
        String completionsPath;
        if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")) {
            completionsPath = "chat/completions";
        } else {
            // DeepSeek官方文档显示路径是 /chat/completions，不带v1前缀
            completionsPath = "chat/completions";
        }
        
        log.info("配置Chat Completions路径: {}", completionsPath);
        
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)  // 明确指定completions路径
                .build();
    }

    /**
     * 创建ChatModel bean
     * 使用Spring AI的自动配置，支持DeepSeek的deepseek-chat模型
     */
    @Bean
    public ChatModel chatModel(OpenAiApi openAiApi) {
        log.info("初始化ChatModel - model: {}, temperature: {}, maxTokens: {}", 
                model, temperature, maxTokens);
        
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        log.info("ChatModel初始化完成");
        return chatModel;
    }
}
