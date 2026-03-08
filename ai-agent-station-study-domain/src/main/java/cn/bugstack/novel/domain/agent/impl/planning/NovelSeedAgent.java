package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import cn.bugstack.novel.types.enums.NovelGenre;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 小说种子Agent
 * 生成或管理小说Seed
 */
@Slf4j
@Component
public class NovelSeedAgent extends AbstractAgent<String, NovelSeed> {
    
    private final IRAGService ragService;
    private final ILLMClient llmClient;
    
    public NovelSeedAgent(IRAGService ragService, ILLMClient llmClient) {
        super(AgentType.NOVEL_SEED);
        this.ragService = ragService;
        this.llmClient = llmClient;
    }
    
    @Override
    protected NovelSeed doExecute(String input, NovelContext context) {
        log.info("开始生成小说Seed，输入: {}", input);
        
        try {
            // 从RAG检索相似风格文本作为参考
            var similarSeeds = ragService.search(input, "zh", 3);
            
            // 构建RAG参考内容
            StringBuilder ragContext = new StringBuilder();
            if (!similarSeeds.isEmpty()) {
                ragContext.append("参考相似风格文本：\n");
                for (var result : similarSeeds) {
                    ragContext.append(result.getContent().substring(0, Math.min(300, result.getContent().length())));
                    ragContext.append("\n---\n");
                }
            }
            
            // 调用LLM生成Seed
            String systemPrompt = "你是一个专业的小说创作助手，擅长根据用户需求生成小说种子（Seed）。" +
                    "Seed包含：标题、题材、核心冲突、世界观设定、主角设定等信息。" +
                    "请根据用户输入，生成一个完整的小说Seed，以JSON格式返回。" +
                    "JSON格式：{\"title\":\"小说标题\",\"genre\":\"题材名称\",\"coreConflict\":\"核心冲突描述\",\"worldSetting\":\"世界观设定\",\"protagonistSetting\":\"主角设定\",\"targetWordCount\":目标字数}";
            
            String userPrompt = String.format("用户需求：\n%s\n\n%s\n\n请生成小说Seed，以JSON格式返回。", 
                    input, ragContext.toString());
            
            String llmResponse = llmClient.call(systemPrompt, userPrompt);
            
            // 解析LLM返回的JSON
            NovelSeed seed = parseLLMResponse(llmResponse, input, context);
            // 用户自定义字数优先于LLM生成的值
            Integer userTargetWordCount = context.getAttribute("targetWordCount");
            if (userTargetWordCount != null && userTargetWordCount > 0) {
                seed.setTargetWordCount(userTargetWordCount);
            }
            seed.setSeedId(UUID.randomUUID().toString());
            seed.setCreateTime(LocalDateTime.now());
            
            log.info("Seed生成完成: {}, 标题: {}", seed.getSeedId(), seed.getTitle());
            return seed;
            
        } catch (Exception e) {
            log.error("生成Seed失败，使用降级策略", e);
            // 降级策略：使用简单解析
            return generateFallbackSeed(input, context);
        }
    }
    
    /**
     * 解析LLM返回的JSON响应
     */
    private NovelSeed parseLLMResponse(String llmResponse, String originalInput, NovelContext context) {
        try {
            // 尝试提取JSON部分（LLM可能返回带说明的文本）
            String jsonStr = extractJsonFromResponse(llmResponse);
            JSONObject json = JSON.parseObject(jsonStr);
            
            Integer targetWordCount = json.getInteger("targetWordCount");
            if (targetWordCount == null || targetWordCount <= 0) {
                targetWordCount = 1000000;
            }
            // 用户自定义字数优先
            Integer userTarget = context != null ? context.getAttribute("targetWordCount") : null;
            if (userTarget != null && userTarget > 0) {
                targetWordCount = userTarget;
            }
            
            return NovelSeed.builder()
                    .title(json.getString("title"))
                    .genre(parseGenre(json.getString("genre")))
                    .coreConflict(json.getString("coreConflict"))
                    .worldSetting(json.getString("worldSetting"))
                    .protagonistSetting(json.getString("protagonistSetting"))
                    .targetWordCount(targetWordCount)
                    .build();
        } catch (Exception e) {
            log.warn("解析LLM响应失败，使用降级策略", e);
            return generateFallbackSeed(originalInput, context);
        }
    }
    
    /**
     * 从LLM响应中提取JSON
     */
    private String extractJsonFromResponse(String response) {
        // 查找JSON对象
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
    
    /**
     * 降级策略：生成基础Seed
     */
    private NovelSeed generateFallbackSeed(String input, NovelContext context) {
        String[] parts = input.split("\n");
        String genreStr = extractValue(parts, "题材:");
        String coreConflict = extractValue(parts, "核心冲突:");
        String worldSetting = extractValue(parts, "世界观:");
        
        Integer targetWordCount = 1000000;
        if (context != null) {
            Integer userTarget = context.getAttribute("targetWordCount");
            if (userTarget != null && userTarget > 0) {
                targetWordCount = userTarget;
            }
        }
        
        return NovelSeed.builder()
                .seedId(UUID.randomUUID().toString())
                .title(generateTitle(genreStr, coreConflict))
                .genre(parseGenre(genreStr))
                .coreConflict(coreConflict.isEmpty() ? "待完善" : coreConflict)
                .worldSetting(worldSetting.isEmpty() ? "待完善" : worldSetting)
                .protagonistSetting("待生成主角设定")
                .targetWordCount(targetWordCount)
                .createTime(LocalDateTime.now())
                .build();
    }
    
    private String extractValue(String[] parts, String prefix) {
        for (String part : parts) {
            if (part.contains(prefix)) {
                return part.substring(part.indexOf(prefix) + prefix.length()).trim();
            }
        }
        return "";
    }
    
    private NovelGenre parseGenre(String genreStr) {
        // 简化处理，实际应该更智能
        if (genreStr.contains("修仙")) {
            return NovelGenre.CULTIVATION;
        } else if (genreStr.contains("历史")) {
            return NovelGenre.HISTORICAL_TRANSMIGRATION;
        } else if (genreStr.contains("都市")) {
            return NovelGenre.URBAN_REBIRTH;
        } else if (genreStr.contains("言情")) {
            return NovelGenre.ROMANCE;
        }
        return NovelGenre.CULTIVATION; // 默认
    }
    
    private String generateTitle(String genre, String conflict) {
        // 降级策略：简单拼接
        if (conflict != null && !conflict.isEmpty()) {
            return genre + "：" + conflict.substring(0, Math.min(10, conflict.length()));
        }
        return genre + "：未知";
    }
    
}
