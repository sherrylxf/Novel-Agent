package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.NovelAgentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 小说 Agent 配置 DAO
 */
@Mapper
public interface INovelAgentConfigDao {

    int insert(NovelAgentConfig config);

    int updateByConfigId(NovelAgentConfig config);

    NovelAgentConfig queryByConfigId(@Param("configId") String configId);

    NovelAgentConfig queryByScopeAndKey(@Param("novelId") String novelId,
                                        @Param("agentType") String agentType,
                                        @Param("configKey") String configKey);

    List<NovelAgentConfig> queryByNovelId(@Param("novelId") String novelId);

    List<NovelAgentConfig> queryGlobalConfigs();

    int deleteByConfigId(@Param("configId") String configId);
}
