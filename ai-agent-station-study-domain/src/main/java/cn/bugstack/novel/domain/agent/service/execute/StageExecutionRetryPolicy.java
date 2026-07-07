package cn.bugstack.novel.domain.agent.service.execute;

import lombok.Builder;
import lombok.Value;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 阶段执行失败时的指数退避策略（与数据库断点恢复配合：失败不推进阶段，仅重试当前步）。
 */
@Value
@Builder
public class StageExecutionRetryPolicy {

    @Builder.Default
    int maxAttempts = 5;

    /** 首次等待（毫秒） */
    @Builder.Default
    long initialDelayMs = 1_000L;

    @Builder.Default
    long maxDelayMs = 30_000L;

    @Builder.Default
    double multiplier = 1.5D;

    /**
     * 在基准退避上乘以 [1-jitter, 1+jitter] 的随机因子，避免多会话齐刷刷重试；0 表示不抖动。
     */
    @Builder.Default
    double jitterRatio = 0.2D;

    public long delayAfterAttemptMs(int attemptIndexOneBased) {
        if (attemptIndexOneBased <= 1) {
            return 0L;
        }
        double d = initialDelayMs * Math.pow(multiplier, attemptIndexOneBased - 2);
        long base = Math.min((long) d, maxDelayMs);
        if (base <= 0 || jitterRatio <= 0) {
            return base;
        }
        double lo = Math.max(0.0, 1.0 - jitterRatio);
        double hi = 1.0 + jitterRatio;
        double factor = ThreadLocalRandom.current().nextDouble(lo, hi);
        return Math.min((long) (base * factor), maxDelayMs);
    }
}
