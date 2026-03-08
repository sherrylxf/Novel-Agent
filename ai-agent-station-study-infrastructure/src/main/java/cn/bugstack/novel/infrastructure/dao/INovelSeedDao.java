package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.NovelSeed;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 小说种子表 DAO
 */
@Mapper
public interface INovelSeedDao {
    
    /**
     * 插入小说Seed
     */
    int insert(NovelSeed novelSeed);
    
    /**
     * 根据Seed ID更新
     */
    int updateBySeedId(NovelSeed novelSeed);
    
    /**
     * 根据Seed ID查询
     */
    NovelSeed queryBySeedId(String seedId);
    
    /**
     * 根据小说ID查询
     */
    List<NovelSeed> queryByNovelId(String novelId);
    
}
