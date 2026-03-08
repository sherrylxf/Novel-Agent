package cn.bugstack.novel.infrastructure.client.llm;

import cn.bugstack.novel.domain.service.llm.ILLMClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM客户端服务类
 * 封装ChatModel调用，提供统一的LLM调用接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Service
public class LLMClient implements ILLMClient {

    private final ChatModel chatModel;

    public LLMClient(@Qualifier("chatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("LLMClient初始化完成");
    }

    /**
     * 同步调用LLM，返回完整响应
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM响应内容
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String call(String systemPrompt, String userPrompt) {
        try {
            log.debug("调用LLM - systemPrompt长度: {}, userPrompt长度: {}", 
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userPrompt != null ? userPrompt.length() : 0);

            List<Message> messages = buildMessages(systemPrompt, userPrompt);
            Prompt prompt = new Prompt(messages);
            
            ChatResponse response = chatModel.call(prompt);
            String result = response.getResult().getOutput().getText();
            
            log.debug("LLM调用成功 - 响应长度: {}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            throw new LLMClientException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用模板构建prompt并调用LLM
     *
     * @param systemPrompt 系统提示词
     * @param template     用户提示词模板
     * @param variables    模板变量
     * @return LLM响应内容
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String callWithTemplate(String systemPrompt, String template, Map<String, Object> variables) {
        try {
            log.debug("使用模板调用LLM - template长度: {}, variables数量: {}", 
                    template != null ? template.length() : 0,
                    variables != null ? variables.size() : 0);

            PromptTemplate promptTemplate = new PromptTemplate(template);
            String userPrompt = promptTemplate.render(variables);
            
            return call(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("使用模板调用LLM失败", e);
            throw new LLMClientException("使用模板调用LLM失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用LLM，返回Flux流
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 流式响应Flux
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        try {
            log.debug("流式调用LLM - systemPrompt长度: {}, userPrompt长度: {}", 
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userPrompt != null ? userPrompt.length() : 0);

            List<Message> messages = buildMessages(systemPrompt, userPrompt);
            Prompt prompt = new Prompt(messages);
            
            Flux<ChatResponse> responseFlux = chatModel.stream(prompt);
            
            return responseFlux
                    .map(response -> {
                        String content = response.getResult().getOutput().getText();
                        log.trace("收到流式响应片段 - 长度: {}", content != null ? content.length() : 0);
                        return content != null ? content : "";
                    })
                    .doOnError(error -> {
                        log.error("流式调用LLM出错", error);
                    })
                    .doOnComplete(() -> {
                        log.debug("流式调用LLM完成");
                    });
        } catch (Exception e) {
            log.error("流式调用LLM失败", e);
            return Flux.error(new LLMClientException("流式调用LLM失败: " + e.getMessage(), e));
        }
    }

    /**
     * 使用模板构建prompt并流式调用LLM
     *
     * @param systemPrompt 系统提示词
     * @param template     用户提示词模板
     * @param variables    模板变量
     * @return 流式响应Flux
     */
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamWithTemplate(String systemPrompt, String template, Map<String, Object> variables) {
        try {
            log.debug("使用模板流式调用LLM - template长度: {}, variables数量: {}", 
                    template != null ? template.length() : 0,
                    variables != null ? variables.size() : 0);

            PromptTemplate promptTemplate = new PromptTemplate(template);
            String userPrompt = promptTemplate.render(variables);
            
            return stream(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("使用模板流式调用LLM失败", e);
            return Flux.error(new LLMClientException("使用模板流式调用LLM失败: " + e.getMessage(), e));
        }
    }

    /**
     * 构建消息列表
     *
     * @param systemPrompt 系统提示词（可选）
     * @param userPrompt   用户提示词
     * @return 消息列表
     */
    private List<Message> buildMessages(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        
        if (userPrompt != null && !userPrompt.trim().isEmpty()) {
            messages.add(new UserMessage(userPrompt));
        }
        
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("系统提示词和用户提示词不能同时为空");
        }
        
        return messages;
    }

    /**
     * LLM客户端异常类
     */
    public static class LLMClientException extends RuntimeException {
        public LLMClientException(String message) {
            super(message);
        }

        public LLMClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
