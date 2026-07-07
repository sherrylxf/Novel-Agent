package cn.bugstack.novel.domain.service.llm;

import cn.bugstack.novel.domain.model.entity.NovelContext;

import java.util.Optional;

/**
 * 在当前线程将 {@link NovelContext} 与正在执行的 Agent 类型（编排注册名）绑定，
 * 供基础设施层 LLM 装饰器解析 novel_agent_config 中的按-Agent 参数。
 */
public final class LLMExecutionContext {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private LLMExecutionContext() {
    }

    public static void enter(NovelContext novelContext, String agentTypeCode) {
        HOLDER.set(new Snapshot(novelContext, agentTypeCode));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Optional<Snapshot> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    public record Snapshot(NovelContext novelContext, String agentTypeCode) {
    }
}
