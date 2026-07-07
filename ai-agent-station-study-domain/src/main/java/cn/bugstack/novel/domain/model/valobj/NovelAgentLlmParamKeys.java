package cn.bugstack.novel.domain.model.valobj;

/**
 * novel_agent_config 中与 LLM 调用相关的 config_key 约定（与 OpenAI 兼容 API 字段对齐）。
 */
public final class NovelAgentLlmParamKeys {

    private NovelAgentLlmParamKeys() {
    }

    public static final String TEMPERATURE = "temperature";
    public static final String MAX_TOKENS = "max_tokens";
    public static final String MAX_TOKENS_CAMEL = "maxTokens";
    public static final String MODEL = "model";
}
