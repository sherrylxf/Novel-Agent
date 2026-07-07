package cn.bugstack.novel.domain.service.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景生成结构化 Prompt 的运行时开关（可由应用层从 yml 注入）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenePromptOptions {

    @Builder.Default
    private boolean fewShotEnabled = true;

    /** Few-Shot 块最大字符数，与「知识简洁性」策略一致 */
    @Builder.Default
    private int maxFewShotChars = 900;

    public static ScenePromptOptions defaults() {
        return builder().build();
    }
}
