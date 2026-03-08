package cn.bugstack.novel.infrastructure.adapter.repository;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelRepository;
import cn.bugstack.novel.domain.model.valobj.NovelSeedVO;
import cn.bugstack.novel.infrastructure.dao.INovelSeedDao;
import cn.bugstack.novel.infrastructure.dao.po.NovelSeed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小说仓储实现
 */
@Slf4j
@Repository
public class NovelRepository implements INovelRepository {
    
    private final INovelSeedDao novelSeedDao;
    
    public NovelRepository(INovelSeedDao novelSeedDao) {
        this.novelSeedDao = novelSeedDao;
    }
    
    @Override
    public NovelSeedVO queryNovelSeedByNovelId(String novelId) {
        List<NovelSeed> seeds = novelSeedDao.queryByNovelId(novelId);
        if (seeds == null || seeds.isEmpty()) {
            return null;
        }
        
        // 取最新的一个
        NovelSeed seed = seeds.get(0);
        return convertToVO(seed);
    }
    
    @Override
    public void saveNovelSeed(NovelSeedVO seedVO) {
        NovelSeed seed = convertToPO(seedVO);
        novelSeedDao.insert(seed);
    }
    
    @Override
    public List<NovelSeedVO> queryNovelList(String genre) {
        // 这里可以扩展查询逻辑
        return List.of();
    }
    
    private NovelSeedVO convertToVO(NovelSeed po) {
        return NovelSeedVO.builder()
                .seedId(po.getSeedId())
                .novelId(po.getNovelId())
                .title(po.getTitle())
                .genre(po.getGenre())
                .coreConflict(po.getCoreConflict())
                .worldSetting(po.getWorldSetting())
                .protagonistSetting(po.getProtagonistSetting())
                .targetWordCount(po.getTargetWordCount())
                .build();
    }
    
    private NovelSeed convertToPO(NovelSeedVO vo) {
        return NovelSeed.builder()
                .seedId(vo.getSeedId())
                .novelId(vo.getNovelId())
                .title(vo.getTitle())
                .genre(vo.getGenre())
                .coreConflict(vo.getCoreConflict())
                .worldSetting(vo.getWorldSetting())
                .protagonistSetting(vo.getProtagonistSetting())
                .targetWordCount(vo.getTargetWordCount())
                .build();
    }
    
}
