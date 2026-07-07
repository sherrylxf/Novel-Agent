package cn.bugstack.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "novel.pipeline")
public class NovelPipelineProperties {

    private Retry retry = new Retry();

    /**
     * 是否将 {@link cn.bugstack.novel.domain.model.entity.NovelContext} 的编排状态写入表 {@code novel_pipeline_checkpoint}。
     */
    private Checkpoint checkpoint = new Checkpoint();

    @Data
    public static class Retry {
        /** 含首次尝试在内的总次数； transient 失败会指数退避直至耗尽 */
        private int maxAttempts = 4;
        /** 首次退避基准（毫秒），与 max-delay、multiplier 共同决定平均等待（目标显著低于数十秒级） */
        private long initialDelayMs = 400L;
        private long maxDelayMs = 5_000L;
        private double multiplier = 2.0D;
        /** 0~0.5 为宜；略抖动可降低并发重试尖峰 */
        private double jitterRatio = 0.15D;
    }

    @Data
    public static class Checkpoint {
        /** 默认开启：与 novel_pipeline_checkpoint 表配合做阶段级断点与失败恢复 */
        private boolean enabled = true;
    }
}
