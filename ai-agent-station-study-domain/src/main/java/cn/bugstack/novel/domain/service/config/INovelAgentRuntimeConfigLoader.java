package cn.bugstack.novel.domain.service.config;

import cn.bugstack.novel.domain.model.valobj.NovelAgentRuntimeConfig;

/**
 * 从持久化加载并合并全局 + 本书的 novel_agent_config。
 */
public interface INovelAgentRuntimeConfigLoader {

    /**
     * @param novelId 可为 null，则仅加载全局配置（novel_id IS NULL）
     */
    NovelAgentRuntimeConfig load(String novelId);
}
