package cn.bugstack.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 场景生成「上下文工程」开关：与 application.yml 中 novel.agent.scene-prompt 对齐。
 */
@Data
@ConfigurationProperties(prefix = "novel.agent.scene-prompt")
public class ScenePromptProperties {

    /** 是否注入静态 Few-Shot 风格块 */
    private boolean fewShotEnabled = true;

    /** Few-Shot 最大字符，平衡 token 与风格引导 */
    private int maxFewShotChars = 900;
}
