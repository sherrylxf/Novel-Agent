package cn.bugstack.novel.test;

import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


/**
 * 小说生成完整流程测试
 * 演示从Seed → 章节生成的完整流程
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class NovelGenerationTest {
    
    @Autowired
    private NovelAgentOrchestrator orchestrator;
    
    /**
     * 完整的小说生成流程测试
     * Seed → 梗概 → 场景 → 正文
     */
    @Test
    public void testCompleteNovelGeneration() {
        log.info("========== 开始小说生成流程 ==========");
        
        // 输入参数
        String genre = "修仙";
        String coreConflict = "一个废材少年的逆袭之路";
        String worldSetting = "修真世界，分为炼气、筑基、金丹、元婴等境界";
        
        try {
            // 执行完整流程
            NovelPlan plan = orchestrator.generateNovel(genre, coreConflict, worldSetting);
            
            log.info("========== 小说生成完成 ==========");
            log.info("小说ID: {}", plan.getNovelId());
            log.info("总卷数: {}", plan.getTotalVolumes());
            log.info("每卷章节数: {}", plan.getChaptersPerVolume());
            log.info("整体大纲: {}", plan.getOverallOutline());
            
        } catch (Exception e) {
            log.error("小说生成失败", e);
            throw e;
        }
    }
    
    /**
     * 测试单个Agent
     */
    @Test
    public void testSingleAgent() {
        log.info("========== 测试单个Agent ==========");
        
        // 这里可以测试单个Agent的功能
        // 例如：只测试NovelSeedAgent
        
    }
    
}
