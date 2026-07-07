package cn.bugstack.novel.domain.service.guard;

import java.util.function.Supplier;

/**
 * 生成链路防护：用户级限流 + 章节场景生成分布式互斥（幂等）。
 * 无 Redis/Redisson 时不注册实现，由调用方按 null 跳过。
 */
public interface INovelGenerationGuard {

    /**
     * 令牌桶风格限流：同一 clientKey（如 sessionId）在窗口内的生成请求次数。
     *
     * @return true 表示获准执行
     */
    boolean tryAcquireGenerateRequest(String clientKey);

    /**
     * 在分布式锁内执行场景生成（Redisson Watchdog 续租，避免长文本生成中途丢锁）。
     */
    <T> T supplyWithSceneGenerationLock(String novelId, Integer volumeNumber, Integer chapterNumber,
                                        Supplier<T> supplier);
}
