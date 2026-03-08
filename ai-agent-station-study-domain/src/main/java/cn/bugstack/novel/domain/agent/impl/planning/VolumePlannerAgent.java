package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 卷/册规划Agent
 * 规划卷/册结构
 */
@Slf4j
@Component
public class VolumePlannerAgent extends AbstractAgent<Object[], VolumePlan> {
    
    private final ILLMClient llmClient;
    
    public VolumePlannerAgent(ILLMClient llmClient) {
        super(AgentType.VOLUME_PLANNER);
        this.llmClient = llmClient;
    }
    
    @Override
    protected VolumePlan doExecute(Object[] input, NovelContext context) {
        NovelPlan plan = (NovelPlan) input[0];
        Integer volumeNumber = (Integer) input[1];
        
        log.info("开始规划卷/册，卷序号: {}", volumeNumber);
        
        try {
            // 调用LLM生成卷标题和主题
            String volumeTitle = generateVolumeTitle(plan, volumeNumber);
            String volumeTheme = generateVolumeTheme(plan, volumeNumber);
            
            VolumePlan volumePlan = VolumePlan.builder()
                    .volumeId(UUID.randomUUID().toString())
                    .novelId(plan != null ? plan.getNovelId() : null)
                    .volumeNumber(volumeNumber)
                    .volumeTitle(volumeTitle)
                    .volumeTheme(volumeTheme)
                    .chapterCount(plan.getChaptersPerVolume())
                    .chapterOutlines(new ArrayList<>())
                    .build();
            
            log.info("卷/册规划完成，卷ID: {}", volumePlan.getVolumeId());
            return volumePlan;
            
        } catch (Exception e) {
            log.error("规划卷失败，使用降级策略", e);
            return generateFallbackVolumePlan(plan, volumeNumber);
        }
    }
    
    /**
     * 调用LLM生成卷标题
     */
    private String generateVolumeTitle(NovelPlan plan, int volumeNumber) {
        if (llmClient == null) {
            return "第" + volumeNumber + "卷";
        }
        
        try {
            String systemPrompt = "你是一个专业的小说规划助手。根据小说整体大纲和卷序号，生成卷标题。";
            String template = "小说整体大纲：\n{overallOutline}\n\n" +
                    "请为第{volumeNumber}卷生成一个吸引人的卷标题（10-20字）。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("overallOutline", plan.getOverallOutline());
            variables.put("volumeNumber", volumeNumber);
            
            String title = llmClient.callWithTemplate(systemPrompt, template, variables);
            String t = title == null ? "" : title.trim().replace("\n", "");
            return t.isEmpty() ? "第" + volumeNumber + "卷" : t.substring(0, Math.min(50, t.length()));
            
        } catch (Exception e) {
            log.warn("生成卷标题失败，使用默认标题", e);
            return "第" + volumeNumber + "卷";
        }
    }
    
    /**
     * 调用LLM生成卷主题
     */
    private String generateVolumeTheme(NovelPlan plan, int volumeNumber) {
        if (llmClient == null) {
            return String.format("第%d卷主题：主角的成长与突破", volumeNumber);
        }
        
        try {
            String systemPrompt = "你是一个专业的小说规划助手。根据小说整体大纲和卷序号，生成卷主题描述。";
            String template = "小说整体大纲：\n{overallOutline}\n\n" +
                    "请为第{volumeNumber}卷生成主题描述（100-200字），包括本卷的主要情节发展、角色成长等。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("overallOutline", plan.getOverallOutline());
            variables.put("volumeNumber", volumeNumber);
            
            return llmClient.callWithTemplate(systemPrompt, template, variables);
            
        } catch (Exception e) {
            log.warn("生成卷主题失败，使用默认主题", e);
            return String.format("第%d卷主题：主角的成长与突破", volumeNumber);
        }
    }
    
    /**
     * 降级策略
     */
    private VolumePlan generateFallbackVolumePlan(NovelPlan plan, int volumeNumber) {
        return VolumePlan.builder()
                .volumeId(UUID.randomUUID().toString())
                .novelId(plan != null ? plan.getNovelId() : null)
                .volumeNumber(volumeNumber)
                .volumeTitle("第" + volumeNumber + "卷")
                .volumeTheme(String.format("第%d卷主题：主角的成长与突破", volumeNumber))
                .chapterCount(plan.getChaptersPerVolume())
                .chapterOutlines(new ArrayList<>())
                .build();
    }
    
}
