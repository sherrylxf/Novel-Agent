package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 章节梗概Agent
 * 生成章节梗概，不写正文
 */
@Slf4j
@Component
public class ChapterOutlineAgent extends AbstractAgent<Object[], ChapterOutline> {
    
    private final ILLMClient llmClient;
    
    public ChapterOutlineAgent(ILLMClient llmClient) {
        super(AgentType.CHAPTER_OUTLINE);
        this.llmClient = llmClient;
    }
    
    @Override
    protected ChapterOutline doExecute(Object[] input, NovelContext context) {
        VolumePlan volumePlan = (VolumePlan) input[0];
        Integer chapterNumber = (Integer) input[1];
        
        log.info("开始生成章节梗概，章节序号: {}", chapterNumber);
        
        try {
            // 调用LLM生成章节梗概
            String chapterTitle = generateChapterTitle(volumePlan, chapterNumber);
            String outline = generateOutline(volumePlan, chapterNumber);
            List<String> keyCharacters = generateKeyCharacters(volumePlan, chapterNumber, outline);
            List<String> keyEvents = generateKeyEvents(volumePlan, chapterNumber);
            List<String> foreshadowing = generateForeshadowing(volumePlan, chapterNumber, outline);
            
            ChapterOutline chapterOutline = ChapterOutline.builder()
                    .chapterId(UUID.randomUUID().toString())
                    .chapterNumber(chapterNumber)
                    .chapterTitle(chapterTitle)
                    .outline(outline)
                    .keyCharacters(keyCharacters)
                    .keyEvents(keyEvents)
                    .foreshadowing(foreshadowing)
                    .scenes(new ArrayList<>())
                    .build();
            
            log.info("章节梗概生成完成，章节ID: {}", chapterOutline.getChapterId());
            return chapterOutline;
            
        } catch (Exception e) {
            log.error("生成章节梗概失败，使用降级策略", e);
            return generateFallbackOutline(volumePlan, chapterNumber);
        }
    }
    
    /**
     * 调用LLM生成章节标题
     */
    private String generateChapterTitle(VolumePlan volumePlan, int chapterNumber) {
        if (llmClient == null) {
            return "第" + chapterNumber + "章";
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据卷主题和章节序号，生成章节标题。";
            String template = "卷主题：{volumeTheme}\n\n请为第{chapterNumber}章生成一个吸引人的章节标题（10-20字）。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            
            String title = llmClient.callWithTemplate(systemPrompt, template, variables);
            return title.trim().replace("\n", "").substring(0, Math.min(50, title.length()));
            
        } catch (Exception e) {
            log.warn("生成章节标题失败，使用默认标题", e);
            return "第" + chapterNumber + "章";
        }
    }
    
    /**
     * 调用LLM生成章节梗概
     */
    private String generateOutline(VolumePlan volumePlan, int chapterNumber) {
        if (llmClient == null) {
            return String.format("第%d章梗概：主角在修炼过程中遇到挑战，通过努力克服困难，获得突破。", chapterNumber);
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据卷主题和章节序号，生成详细的章节梗概（200-300字）。";
            String template = "卷主题：{volumeTheme}\n\n请为第{chapterNumber}章生成详细的章节梗概，包括主要情节发展、关键事件等。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            
            return llmClient.callWithTemplate(systemPrompt, template, variables);
            
        } catch (Exception e) {
            log.warn("生成章节梗概失败，使用默认梗概", e);
            return String.format("第%d章梗概：主角在修炼过程中遇到挑战，通过努力克服困难，获得突破。", chapterNumber);
        }
    }
    
    /**
     * 调用LLM提取关键人物（基于章节梗概，确保主角、配角、势力代表等均被纳入）
     */
    private List<String> generateKeyCharacters(VolumePlan volumePlan, int chapterNumber, String outline) {
        if (llmClient == null) {
            return Arrays.asList("主角", "配角");
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据章节梗概，提取本章出现的所有关键人物（含主角、配角、势力代表、对手、盟友等），以JSON数组格式返回。务必列举具体人名，不要用\"主角\"\"配角\"等泛称。";
            String template = "卷主题：{volumeTheme}\n章节序号：{chapterNumber}\n章节梗概：{outline}\n\n请从梗概中提取本章登场的所有人物（具体人名），以JSON数组返回，例如：[\"林衍\",\"艾薇\",\"星盟指挥官\"]。至少包含主角，若有其他势力、对手、盟友等也需列入。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            variables.put("outline", outline != null ? outline : "");
            
            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            // 提取JSON数组
            String jsonArray = extractJsonArray(response);
            return JSON.parseArray(jsonArray, String.class);
            
        } catch (Exception e) {
            log.warn("提取关键人物失败，使用默认人物", e);
            return Arrays.asList("主角", "配角");
        }
    }
    
    /**
     * 调用LLM提取关键事件
     */
    private List<String> generateKeyEvents(VolumePlan volumePlan, int chapterNumber) {
        if (llmClient == null) {
            return Arrays.asList("修炼突破", "遇到挑战", "获得机缘");
        }
        
        try {
            String systemPrompt = "你是一个专业的小说创作助手。根据章节梗概，提取关键事件列表，以JSON数组格式返回。";
            String template = "卷主题：{volumeTheme}\n章节序号：{chapterNumber}\n\n请提取本章的关键事件列表，以JSON数组格式返回，例如：[\"事件1\",\"事件2\"]";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            
            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            // 提取JSON数组
            String jsonArray = extractJsonArray(response);
            return JSON.parseArray(jsonArray, String.class);
            
        } catch (Exception e) {
            log.warn("提取关键事件失败，使用默认事件", e);
            return Arrays.asList("修炼突破", "遇到挑战", "获得机缘");
        }
    }

    /**
     * 生成本章引入或推进的伏笔/剧情线程。
     */
    private List<String> generateForeshadowing(VolumePlan volumePlan, int chapterNumber, String outline) {
        if (llmClient == null) {
            return new ArrayList<>();
        }

        try {
            String systemPrompt = "你是一个专业的小说策划编辑。请根据章节梗概，提取本章新增或推进的伏笔/剧情线程，以 JSON 数组格式返回。";
            String template = "卷主题：{volumeTheme}\n章节序号：{chapterNumber}\n章节梗概：{outline}\n\n" +
                    "请提取 0-3 个需要在后续章节持续追踪的伏笔或剧情线程，返回 JSON 数组，例如：[\"主角身世线索\",\"宗门试炼背后的阴谋\"]。";

            Map<String, Object> variables = new HashMap<>();
            variables.put("volumeTheme", volumePlan.getVolumeTheme());
            variables.put("chapterNumber", chapterNumber);
            variables.put("outline", outline);

            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            String jsonArray = extractJsonArray(response);
            return JSON.parseArray(jsonArray, String.class);
        } catch (Exception e) {
            log.warn("提取伏笔失败，使用空列表", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 从响应中提取JSON数组
     */
    private String extractJsonArray(String response) {
        int start = response.indexOf("[");
        int end = response.lastIndexOf("]");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "[]";
    }
    
    /**
     * 降级策略
     */
    private ChapterOutline generateFallbackOutline(VolumePlan volumePlan, int chapterNumber) {
        return ChapterOutline.builder()
                .chapterId(UUID.randomUUID().toString())
                .chapterNumber(chapterNumber)
                .chapterTitle("第" + chapterNumber + "章")
                .outline(String.format("第%d章梗概：主角在修炼过程中遇到挑战，通过努力克服困难，获得突破。", chapterNumber))
                .keyCharacters(Arrays.asList("主角", "配角"))
                .keyEvents(Arrays.asList("修炼突破", "遇到挑战", "获得机缘"))
                .foreshadowing(new ArrayList<>())
                .scenes(new ArrayList<>())
                .build();
    }
}
