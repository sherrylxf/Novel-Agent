package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 小说规划Agent
 * 规划小说整体结构
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class NovelPlannerAgent extends AbstractAgent<NovelSeed, NovelPlan> {
    
    public NovelPlannerAgent() {
        super(AgentType.NOVEL_PLANNER);
    }
    
    @Override
    protected NovelPlan doExecute(NovelSeed seed, NovelContext context) {
        log.info("开始规划小说，Seed: {}", seed.getSeedId());
        
        // 根据目标字数计算卷数和章节数
        // 假设每章3000字，每卷20章
        int chaptersPerVolume = 20;
        int wordsPerChapter = 3000;
        int totalChapters = seed.getTargetWordCount() / wordsPerChapter;
        int totalVolumes = (totalChapters + chaptersPerVolume - 1) / chaptersPerVolume;
        
        // 构建规划（实际应该调用LLM生成详细大纲）
        NovelPlan plan = NovelPlan.builder()
                .planId(UUID.randomUUID().toString())
                .novelId(context.getNovelId())
                .totalVolumes(totalVolumes)
                .chaptersPerVolume(chaptersPerVolume)
                .volumePlans(new ArrayList<>())
                .overallOutline(generateOverallOutline(seed))
                .build();
        
        log.info("小说规划完成，总卷数: {}, 每卷章节数: {}", totalVolumes, chaptersPerVolume);
        return plan;
    }
    
    private String generateOverallOutline(NovelSeed seed) {
        // 简化处理，实际应该调用LLM生成
        return String.format("小说《%s》的整体大纲：\n" +
                "题材：%s\n" +
                "核心冲突：%s\n" +
                "世界观：%s\n" +
                "预计分为%d卷，每卷%d章",
                seed.getTitle(),
                seed.getGenre().getName(),
                seed.getCoreConflict(),
                seed.getWorldSetting(),
                // 这里应该从plan中获取，简化处理
                5, 20);
    }
    
}
