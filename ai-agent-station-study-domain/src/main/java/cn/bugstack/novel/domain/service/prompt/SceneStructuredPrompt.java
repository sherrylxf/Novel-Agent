package cn.bugstack.novel.domain.service.prompt;

import java.util.Map;

/**
 * 场景生成用「结构化提示」载体：与 {@link SceneStructuredPromptBuilder} 配套，
 * 供 {@link cn.bugstack.novel.domain.service.llm.ILLMClient#callWithTemplate(String, String, Map)} 一次性消费。
 *
 * @param systemPrompt  System 层：题材/全局创作原则（与 user 侧规则互补）
 * @param userTemplate  用户消息模板，含【锚点 / 指令 / 记忆 / RAG / 规则 / Few-Shot】等分段占位符
 * @param variables     模板变量；会以不可变快照形式保存，避免调用方后续修改 Map 影响已构建的 Prompt
 */
public record SceneStructuredPrompt(String systemPrompt, String userTemplate, Map<String, Object> variables) {

    public SceneStructuredPrompt {
        systemPrompt = systemPrompt != null ? systemPrompt : "";
        userTemplate = userTemplate != null ? userTemplate : "";
        variables = variables != null && !variables.isEmpty() ? Map.copyOf(variables) : Map.of();
    }
}
