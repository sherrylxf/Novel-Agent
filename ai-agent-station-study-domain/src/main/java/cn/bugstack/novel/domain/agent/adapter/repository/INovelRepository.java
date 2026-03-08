package cn.bugstack.novel.domain.agent.adapter.repository;

import cn.bugstack.novel.domain.model.valobj.NovelSeedVO;

import java.util.List;

/**
 * 小说仓储接口
 * 领域层定义的仓储接口，由基础设施层实现
 */
public interface INovelRepository {
    
    /**
     * 根据小说ID查询Seed
     */
    NovelSeedVO queryNovelSeedByNovelId(String novelId);
    
    /**
     * 保存Seed
     */
    void saveNovelSeed(NovelSeedVO seed);
    
    /**
     * 查询小说列表
     */
    List<NovelSeedVO> queryNovelList(String genre);
    
}
