package cn.bugstack.novel.infrastructure.config;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelWorkspaceRepository;
import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import cn.bugstack.novel.domain.model.valobj.NovelAgentRuntimeConfig;
import cn.bugstack.novel.domain.service.config.INovelAgentRuntimeConfigLoader;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从 novel_agent_config 表加载配置（与 {@link INovelWorkspaceRepository#queryConfigs} 语义一致：先全局后本书，后者覆盖前者）。
 */
@Service
public class NovelAgentRuntimeConfigLoader implements INovelAgentRuntimeConfigLoader {

    @Resource
    private INovelWorkspaceRepository novelWorkspaceRepository;

    @Override
    public NovelAgentRuntimeConfig load(String novelId) {
        List<NovelAgentConfigItem> items = novelWorkspaceRepository.queryConfigs(novelId, true);
        return NovelAgentRuntimeConfig.fromOrderedItems(items);
    }
}
