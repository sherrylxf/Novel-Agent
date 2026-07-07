package cn.bugstack.novel.infrastructure.client.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 封装 ChatModel 的同步/流式调用；由 {@link ContextualLLMClient} 对外实现 {@link cn.bugstack.novel.domain.service.llm.ILLMClient}。
 */
@Slf4j
public class LLMClient {

    private final ChatModel chatModel;

    public LLMClient(ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("LLMClient（核心实现）初始化完成");
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public String callWithOptions(String systemPrompt, String userPrompt, ChatOptions chatOptions) {
        try {
            log.debug("调用LLM - systemPrompt长度: {}, userPrompt长度: {}, 带运行时选项: {}",
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userPrompt != null ? userPrompt.length() : 0,
                    chatOptions != null);

            List<Message> messages = buildMessages(systemPrompt, userPrompt);
            Prompt prompt = toPrompt(messages, chatOptions);

            ChatResponse response = chatModel.call(prompt);
            String result = response.getResult().getOutput().getText();

            log.debug("LLM调用成功 - 响应长度: {}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("LLM调用失败", e);
            throw new LLMClientException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public String callWithTemplateAndOptions(String systemPrompt, String template, Map<String, Object> variables,
                                            ChatOptions chatOptions) {
        try {
            log.debug("使用模板调用LLM - template长度: {}, variables数量: {}",
                    template != null ? template.length() : 0,
                    variables != null ? variables.size() : 0);

            PromptTemplate promptTemplate = new PromptTemplate(template);
            String userPrompt = promptTemplate.render(variables);

            return callWithOptions(systemPrompt, userPrompt, chatOptions);
        } catch (Exception e) {
            log.error("使用模板调用LLM失败", e);
            throw new LLMClientException("使用模板调用LLM失败: " + e.getMessage(), e);
        }
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamWithOptions(String systemPrompt, String userPrompt, ChatOptions chatOptions) {
        try {
            log.debug("流式调用LLM - systemPrompt长度: {}, userPrompt长度: {}",
                    systemPrompt != null ? systemPrompt.length() : 0,
                    userPrompt != null ? userPrompt.length() : 0);

            List<Message> messages = buildMessages(systemPrompt, userPrompt);
            Prompt prompt = toPrompt(messages, chatOptions);

            Flux<ChatResponse> responseFlux = chatModel.stream(prompt);

            return responseFlux
                    .map(response -> {
                        String content = response.getResult().getOutput().getText();
                        log.trace("收到流式响应片段 - 长度: {}", content != null ? content.length() : 0);
                        return content != null ? content : "";
                    })
                    .doOnError(error -> log.error("流式调用LLM出错", error))
                    .doOnComplete(() -> log.debug("流式调用LLM完成"));
        } catch (Exception e) {
            log.error("流式调用LLM失败", e);
            return Flux.error(new LLMClientException("流式调用LLM失败: " + e.getMessage(), e));
        }
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public Flux<String> streamWithTemplateAndOptions(String systemPrompt, String template, Map<String, Object> variables,
                                                       ChatOptions chatOptions) {
        try {
            log.debug("使用模板流式调用LLM - template长度: {}, variables数量: {}",
                    template != null ? template.length() : 0,
                    variables != null ? variables.size() : 0);

            PromptTemplate promptTemplate = new PromptTemplate(template);
            String userPrompt = promptTemplate.render(variables);

            return streamWithOptions(systemPrompt, userPrompt, chatOptions);
        } catch (Exception e) {
            log.error("使用模板流式调用LLM失败", e);
            return Flux.error(new LLMClientException("使用模板流式调用LLM失败: " + e.getMessage(), e));
        }
    }

    private static Prompt toPrompt(List<Message> messages, ChatOptions chatOptions) {
        if (chatOptions != null) {
            return new Prompt(messages, chatOptions);
        }
        return new Prompt(messages);
    }

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

    public static class LLMClientException extends RuntimeException {
        public LLMClientException(String message) {
            super(message);
        }

        public LLMClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
