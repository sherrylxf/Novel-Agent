package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 小说规划Agent
 * 规划小说整体结构
 */
@Slf4j
@Component
public class NovelPlannerAgent extends AbstractAgent<NovelSeed, NovelPlan> {
    
    private final ILLMClient llmClient;
    
    public NovelPlannerAgent(ILLMClient llmClient) {
        super(AgentType.NOVEL_PLANNER);
        this.llmClient = llmClient;
    }
    
    @Override
    protected NovelPlan doExecute(NovelSeed seed, NovelContext context) {
        log.info("开始规划小说，Seed: {}", seed.getSeedId());
        
        try {
            // 根据目标字数计算卷数和章节数
            int chaptersPerVolume = 20;
            int wordsPerChapter = 3000;
            int totalChapters = seed.getTargetWordCount() / wordsPerChapter;
            int totalVolumes = (totalChapters + chaptersPerVolume - 1) / chaptersPerVolume;
            
            // 调用LLM生成整体大纲
            String overallOutline = generateOverallOutline(seed, totalVolumes, chaptersPerVolume);
            
            NovelPlan plan = NovelPlan.builder()
                    .planId(UUID.randomUUID().toString())
                    .novelId(context.getNovelId())
                    .totalVolumes(totalVolumes)
                    .chaptersPerVolume(chaptersPerVolume)
                    .volumePlans(new ArrayList<>())
                    .overallOutline(overallOutline)
                    .build();
            
            log.info("小说规划完成，总卷数: {}, 每卷章节数: {}", totalVolumes, chaptersPerVolume);
            return plan;
            
        } catch (Exception e) {
            log.error("规划小说失败，使用降级策略", e);
            return generateFallbackPlan(seed, context);
        }
    }
    
    /**
     * 调用LLM生成整体大纲
     */
    private String generateOverallOutline(NovelSeed seed, int totalVolumes, int chaptersPerVolume) {
        if (llmClient == null) {
            return generateFallbackOutline(seed, totalVolumes, chaptersPerVolume);
        }
        
        try {
            String systemPrompt = "你是一个专业的小说规划助手。根据小说Seed信息，生成一个详细的小说整体大纲。" +
                    "大纲应该包括：故事主线、主要情节发展、各卷主题概述等。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("title", seed.getTitle());
            variables.put("genre", seed.getGenre().getName());
            variables.put("coreConflict", seed.getCoreConflict());
            variables.put("worldSetting", seed.getWorldSetting());
            variables.put("protagonistSetting", seed.getProtagonistSetting());
            variables.put("targetWordCount", seed.getTargetWordCount());
            variables.put("totalVolumes", totalVolumes);
            variables.put("chaptersPerVolume", chaptersPerVolume);
            
            String template = "请为小说《{title}》生成整体大纲。\n\n" +
                    "题材：{genre}\n" +
                    "核心冲突：{coreConflict}\n" +
                    "世界观：{worldSetting}\n" +
                    "主角设定：{protagonistSetting}\n" +
                    "目标字数：{targetWordCount}字\n" +
                    "规划结构：{totalVolumes}卷，每卷{chaptersPerVolume}章\n\n" +
                    "请生成详细的小说整体大纲，包括故事主线、主要情节发展、各卷主题概述等。";
            
            return llmClient.callWithTemplate(systemPrompt, template, variables);
            
        } catch (Exception e) {
            log.warn("调用LLM生成大纲失败，使用降级策略", e);
            return generateFallbackOutline(seed, totalVolumes, chaptersPerVolume);
        }
    }
    
    /**
     * 降级策略：生成基础大纲
     */
    private String generateFallbackOutline(NovelSeed seed, int totalVolumes, int chaptersPerVolume) {
        return String.format("小说《%s》的整体大纲：\n" +
                "题材：%s\n" +
                "核心冲突：%s\n" +
                "世界观：%s\n" +
                "预计分为%d卷，每卷%d章\n" +
                "故事将围绕核心冲突展开，主角将在世界观中成长和突破。",
                seed.getTitle(),
                seed.getGenre().getName(),
                seed.getCoreConflict(),
                seed.getWorldSetting(),
                totalVolumes, chaptersPerVolume);
    }
    
    /**
     * 降级策略：生成基础规划
     */
    private NovelPlan generateFallbackPlan(NovelSeed seed, NovelContext context) {
        int chaptersPerVolume = 20;
        int wordsPerChapter = 3000;
        int totalChapters = seed.getTargetWordCount() / wordsPerChapter;
        int totalVolumes = (totalChapters + chaptersPerVolume - 1) / chaptersPerVolume;
        
        return NovelPlan.builder()
                .planId(UUID.randomUUID().toString())
                .novelId(context.getNovelId())
                .totalVolumes(totalVolumes)
                .chaptersPerVolume(chaptersPerVolume)
                .volumePlans(new ArrayList<>())
                .overallOutline(generateFallbackOutline(seed, totalVolumes, chaptersPerVolume))
                .build();
    }
    
}
