package cn.bugstack.novel.infrastructure.client.llm;

import cn.bugstack.novel.domain.model.valobj.NovelAgentLlmParamKeys;
import cn.bugstack.novel.domain.model.valobj.NovelAgentRuntimeConfig;
import cn.bugstack.novel.domain.model.valobj.NovelContextKeys;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.domain.service.llm.LLMExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;

/**
 * 在调用底层 {@link LLMClient} 前，根据 novel_agent_config 与当前 Agent 类型合并 OpenAI 兼容参数（temperature、max_tokens、model）。
 */
@Slf4j
@RequiredArgsConstructor
public class ContextualLLMClient implements ILLMClient {

    private final LLMClient delegate;

    @Override
    public String call(String systemPrompt, String userPrompt) {
        return delegate.callWithOptions(systemPrompt, userPrompt, resolveChatOptions());
    }

    @Override
    public String callWithTemplate(String systemPrompt, String template, Map<String, Object> variables) {
        return delegate.callWithTemplateAndOptions(systemPrompt, template, variables, resolveChatOptions());
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        return delegate.streamWithOptions(systemPrompt, userPrompt, resolveChatOptions());
    }

    @Override
    public Flux<String> streamWithTemplate(String systemPrompt, String template, Map<String, Object> variables) {
        return delegate.streamWithTemplateAndOptions(systemPrompt, template, variables, resolveChatOptions());
    }

    private ChatOptions resolveChatOptions() {
        return LLMExecutionContext.current()
                .flatMap(s -> buildOptions(s.novelContext().getAttribute(NovelContextKeys.AGENT_RUNTIME_CONFIG), s.agentTypeCode()))
                .orElse(null);
    }

    private Optional<OpenAiChatOptions> buildOptions(Object cfgObj, String agentTypeCode) {
        if (!(cfgObj instanceof NovelAgentRuntimeConfig cfg) || agentTypeCode == null || agentTypeCode.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> p = cfg.paramsForAgent(agentTypeCode.trim());
        if (p.isEmpty()) {
            return Optional.empty();
        }
        OpenAiChatOptions.Builder b = OpenAiChatOptions.builder();
        boolean any = false;

        Double t = parseDouble(firstNonBlank(
                cfg.get(agentTypeCode, NovelAgentLlmParamKeys.TEMPERATURE)));
        if (t != null) {
            b.temperature(t);
            any = true;
        }
        Integer mt = parseInt(firstNonBlank(
                cfg.get(agentTypeCode, NovelAgentLlmParamKeys.MAX_TOKENS),
                cfg.get(agentTypeCode, NovelAgentLlmParamKeys.MAX_TOKENS_CAMEL)));
        if (mt != null) {
            b.maxTokens(mt);
            any = true;
        }
        String model = firstNonBlank(cfg.get(agentTypeCode, NovelAgentLlmParamKeys.MODEL));
        if (model != null) {
            b.model(model);
            any = true;
        }
        if (!any) {
            return Optional.empty();
        }
        OpenAiChatOptions built = b.build();
        log.debug("LLM 调用使用 novel_agent_config 覆盖: agentType={}, options={}", agentTypeCode, built);
        return Optional.of(built);
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
