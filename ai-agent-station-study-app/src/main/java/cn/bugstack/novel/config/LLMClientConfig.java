package cn.bugstack.novel.config;

import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.infrastructure.client.llm.ContextualLLMClient;
import cn.bugstack.novel.infrastructure.client.llm.LLMClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

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

    @Value("${novel.llm.http.read-timeout:300}")
    private Integer readTimeoutSeconds;

    @Value("${novel.llm.http.proxy-host:}")
    private String proxyHost;

    @Value("${novel.llm.http.proxy-port:0}")
    private Integer proxyPort;

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

        String completionsPath;
        if (baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")) {
            completionsPath = "chat/completions";
        } else {
            // DeepSeek官方文档显示路径是 /chat/completions，不带v1前缀
            completionsPath = "chat/completions";
        }
        
        log.info("配置Chat Completions路径: {}", completionsPath);

        // 使用 SimpleClientHttpRequestFactory（HttpURLConnection）
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(60));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        // 若配置了代理（Connection reset 时可尝试通过代理访问 DeepSeek）
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            log.info("已配置 HTTP 代理: {}:{}", proxyHost, proxyPort);
        }
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);
        log.info("已配置 SimpleClientHttpRequestFactory，连接超时 60s，读取超时 {}s", readTimeoutSeconds);

        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .restClientBuilder(restClientBuilder)
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

    /**
     * 直连 ChatModel 的调用实现（无 novel_agent_config 合并）。
     */
    @Bean("llmClientCore")
    public LLMClient llmClientCore(ChatModel chatModel) {
        return new LLMClient(chatModel);
    }

    /**
     * 对外统一的 ILLMClient：按当前线程 Agent 与上下文合并 DB 中的 LLM 参数。
     */
    @Bean
    @Primary
    public ILLMClient llmClientFacade(@Qualifier("llmClientCore") LLMClient llmClientCore) {
        return new ContextualLLMClient(llmClientCore);
    }
}
