package cn.bugstack.novel.infrastructure.guard;

import cn.bugstack.novel.domain.service.guard.INovelGenerationGuard;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 基于 Redisson 的用户级限流（令牌桶）与章节场景互斥锁（Watchdog 自动续租）。
 */
@Slf4j
@Service
@ConditionalOnBean(RedissonClient.class)
public class RedissonNovelGenerationGuard implements INovelGenerationGuard {

    private final RedissonClient redissonClient;
    private final int ratePerMinute;

    public RedissonNovelGenerationGuard(RedissonClient redissonClient,
                                        @Value("${novel.guard.rate-per-minute:30}") int ratePerMinute) {
        this.redissonClient = redissonClient;
        this.ratePerMinute = Math.max(1, ratePerMinute);
    }

    @Override
    public boolean tryAcquireGenerateRequest(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return true;
        }
        RRateLimiter limiter = redissonClient.getRateLimiter("novel:guard:rate:" + clientKey);
        limiter.trySetRate(RateType.OVERALL, ratePerMinute, 1, RateIntervalUnit.MINUTES);
        boolean ok = limiter.tryAcquire(1);
        if (!ok) {
            log.warn("生成请求被限流，clientKey={}", clientKey);
        }
        return ok;
    }

    @Override
    public <T> T supplyWithSceneGenerationLock(String novelId, Integer volumeNumber, Integer chapterNumber,
                                                 Supplier<T> supplier) {
        if (novelId == null || novelId.isBlank()) {
            return supplier.get();
        }
        int vol = volumeNumber != null ? volumeNumber : 0;
        int ch = chapterNumber != null ? chapterNumber : 0;
        RLock lock = redissonClient.getLock("novel:guard:scene:" + novelId + ":" + vol + ":" + ch);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
