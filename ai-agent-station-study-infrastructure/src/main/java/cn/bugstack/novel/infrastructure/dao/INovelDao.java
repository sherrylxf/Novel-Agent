package cn.bugstack.novel.infrastructure.dao;

import cn.bugstack.novel.infrastructure.dao.po.Novel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 小说表 DAO
 */
@Mapper
public interface INovelDao {
    
    /**
     * 插入小说
     */
    int insert(Novel novel);
    
    /**
     * 根据小说ID更新
     */
    int updateByNovelId(Novel novel);
    
    /**
     * 根据小说ID查询
     */
    Novel queryByNovelId(String novelId);
    
    /**
     * 查询所有启用的小说
     */
    List<Novel> queryEnabledNovels();
    
    /**
     * 根据题材查询
     */
    List<Novel> queryByGenre(String genre);
    
}
