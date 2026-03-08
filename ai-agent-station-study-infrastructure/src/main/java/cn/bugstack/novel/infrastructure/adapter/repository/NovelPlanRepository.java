package cn.bugstack.novel.infrastructure.adapter.repository;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelPlanRepository;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.infrastructure.dao.INovelPlanDao;
import cn.bugstack.novel.infrastructure.dao.IVolumePlanDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 小说规划仓储实现
 */
@Slf4j
@Repository
public class NovelPlanRepository implements INovelPlanRepository {
    
    private final INovelPlanDao novelPlanDao;
    private final IVolumePlanDao volumePlanDao;
    
    public NovelPlanRepository(INovelPlanDao novelPlanDao, IVolumePlanDao volumePlanDao) {
        this.novelPlanDao = novelPlanDao;
        this.volumePlanDao = volumePlanDao;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void saveNovelPlan(NovelPlan novelPlan) {
        try {
            log.info("开始保存小说规划，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId());
            
            // 保存主规划（支持重复保存：存在则更新）
            cn.bugstack.novel.infrastructure.dao.po.NovelPlan planPO = convertToPO(novelPlan);
            cn.bugstack.novel.infrastructure.dao.po.NovelPlan existing = novelPlanDao.queryByPlanId(novelPlan.getPlanId());
            if (existing == null) {
                int insertResult = novelPlanDao.insert(planPO);
                log.info("插入主规划结果：{}", insertResult);
            } else {
                int updateResult = novelPlanDao.updateByPlanId(planPO);
                log.info("更新主规划结果：{}", updateResult);
            }
            
            // 保存卷规划列表
            if (novelPlan.getVolumePlans() != null && !novelPlan.getVolumePlans().isEmpty()) {
                // 为避免重复，先删除再插入（volume_plan 以 novel_id 维度维护）
                int deleteResult = volumePlanDao.deleteByNovelId(novelPlan.getNovelId());
                log.info("删除旧卷规划结果：{}", deleteResult);
                List<cn.bugstack.novel.infrastructure.dao.po.VolumePlan> volumePlanPOs = novelPlan.getVolumePlans().stream()
                        .map(this::convertVolumePlanToPO)
                        .collect(Collectors.toList());
                int batchInsertResult = volumePlanDao.batchInsert(volumePlanPOs);
                log.info("批量插入卷规划结果：{}，数量：{}", batchInsertResult, volumePlanPOs.size());
            } else {
                log.warn("卷规划列表为空，跳过保存");
            }
            
            log.info("保存小说规划成功，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId());
        } catch (Exception e) {
            log.error("保存小说规划失败，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId(), e);
            throw e;
        }
    }
    
    @Override
    public NovelPlan queryByPlanId(String planId) {
        cn.bugstack.novel.infrastructure.dao.po.NovelPlan planPO = novelPlanDao.queryByPlanId(planId);
        if (planPO == null) {
            return null;
        }
        
        NovelPlan novelPlan = convertToEntity(planPO);
        
        // 查询关联的卷规划
        List<cn.bugstack.novel.infrastructure.dao.po.VolumePlan> volumePlanPOs = volumePlanDao.queryByNovelId(planPO.getNovelId());
        if (volumePlanPOs != null && !volumePlanPOs.isEmpty()) {
            List<VolumePlan> volumePlans = volumePlanPOs.stream()
                    .map(this::convertVolumePlanToEntity)
                    .collect(Collectors.toList());
            novelPlan.setVolumePlans(volumePlans);
        }
        
        return novelPlan;
    }
    
    @Override
    public NovelPlan queryByNovelId(String novelId) {
        List<cn.bugstack.novel.infrastructure.dao.po.NovelPlan> planPOs = novelPlanDao.queryByNovelId(novelId);
        if (planPOs == null || planPOs.isEmpty()) {
            return null;
        }
        
        // 取最新的一个
        cn.bugstack.novel.infrastructure.dao.po.NovelPlan planPO = planPOs.get(0);
        NovelPlan novelPlan = convertToEntity(planPO);
        
        // 查询关联的卷规划
        List<cn.bugstack.novel.infrastructure.dao.po.VolumePlan> volumePlanPOs = volumePlanDao.queryByNovelId(novelId);
        if (volumePlanPOs != null && !volumePlanPOs.isEmpty()) {
            List<VolumePlan> volumePlans = volumePlanPOs.stream()
                    .map(this::convertVolumePlanToEntity)
                    .collect(Collectors.toList());
            novelPlan.setVolumePlans(volumePlans);
        }
        
        return novelPlan;
    }
    
    @Override
    public List<NovelPlan> queryAllByNovelId(String novelId) {
        List<cn.bugstack.novel.infrastructure.dao.po.NovelPlan> planPOs = novelPlanDao.queryByNovelId(novelId);
        if (planPOs == null || planPOs.isEmpty()) {
            return List.of();
        }
        
        return planPOs.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "transactionManager")
    public void updateNovelPlan(NovelPlan novelPlan) {
        try {
            log.info("开始更新小说规划，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId());
            
            // 更新主规划
            cn.bugstack.novel.infrastructure.dao.po.NovelPlan planPO = convertToPO(novelPlan);
            int updateResult = novelPlanDao.updateByPlanId(planPO);
            log.info("更新主规划结果：{}", updateResult);
            
            // 删除旧的卷规划，重新插入
            if (novelPlan.getVolumePlans() != null && !novelPlan.getVolumePlans().isEmpty()) {
                int deleteResult = volumePlanDao.deleteByNovelId(novelPlan.getNovelId());
                log.info("删除旧卷规划结果：{}", deleteResult);
                
                List<cn.bugstack.novel.infrastructure.dao.po.VolumePlan> volumePlanPOs = novelPlan.getVolumePlans().stream()
                        .map(this::convertVolumePlanToPO)
                        .collect(Collectors.toList());
                int batchInsertResult = volumePlanDao.batchInsert(volumePlanPOs);
                log.info("批量插入卷规划结果：{}，数量：{}", batchInsertResult, volumePlanPOs.size());
            } else {
                log.warn("卷规划列表为空，跳过更新");
            }
            
            log.info("更新小说规划成功，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId());
        } catch (Exception e) {
            log.error("更新小说规划失败，planId: {}, novelId: {}", novelPlan.getPlanId(), novelPlan.getNovelId(), e);
            throw e;
        }
    }
    
    private cn.bugstack.novel.infrastructure.dao.po.NovelPlan convertToPO(NovelPlan entity) {
        return cn.bugstack.novel.infrastructure.dao.po.NovelPlan.builder()
                .planId(entity.getPlanId())
                .novelId(entity.getNovelId())
                .totalVolumes(entity.getTotalVolumes())
                .chaptersPerVolume(entity.getChaptersPerVolume())
                .overallOutline(entity.getOverallOutline())
                .build();
    }
    
    private NovelPlan convertToEntity(cn.bugstack.novel.infrastructure.dao.po.NovelPlan po) {
        return NovelPlan.builder()
                .planId(po.getPlanId())
                .novelId(po.getNovelId())
                .totalVolumes(po.getTotalVolumes())
                .chaptersPerVolume(po.getChaptersPerVolume())
                .overallOutline(po.getOverallOutline())
                .build();
    }
    
    private cn.bugstack.novel.infrastructure.dao.po.VolumePlan convertVolumePlanToPO(VolumePlan entity) {
        return cn.bugstack.novel.infrastructure.dao.po.VolumePlan.builder()
                .volumeId(entity.getVolumeId())
                .novelId(entity.getNovelId())
                .volumeNumber(entity.getVolumeNumber())
                .volumeTitle(entity.getVolumeTitle())
                .volumeTheme(entity.getVolumeTheme())
                .chapterCount(entity.getChapterCount())
                .build();
    }
    
    private VolumePlan convertVolumePlanToEntity(cn.bugstack.novel.infrastructure.dao.po.VolumePlan po) {
        return VolumePlan.builder()
                .volumeId(po.getVolumeId())
                .novelId(po.getNovelId())
                .volumeNumber(po.getVolumeNumber())
                .volumeTitle(po.getVolumeTitle())
                .volumeTheme(po.getVolumeTheme())
                .chapterCount(po.getChapterCount())
                .build();
    }
    
}
