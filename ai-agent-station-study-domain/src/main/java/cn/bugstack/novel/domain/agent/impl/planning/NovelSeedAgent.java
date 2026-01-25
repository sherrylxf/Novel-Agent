package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.types.enums.AgentType;
import cn.bugstack.novel.types.enums.NovelGenre;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 小说种子Agent
 * 生成或管理小说Seed
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class NovelSeedAgent extends AbstractAgent<String, NovelSeed> {
    
    private final IRAGService ragService;
    
    public NovelSeedAgent(IRAGService ragService) {
        super(AgentType.NOVEL_SEED);
        this.ragService = ragService;
    }
    
    @Override
    protected NovelSeed doExecute(String input, NovelContext context) {
        log.info("开始生成小说Seed，输入: {}", input);
        
        // 解析输入（实际应该调用LLM）
        // 这里简化处理，实际应该调用LLM生成
        String[] parts = input.split("\n");
        String genreStr = extractValue(parts, "题材:");
        String coreConflict = extractValue(parts, "核心冲突:");
        String worldSetting = extractValue(parts, "世界观:");
        
        // 从RAG检索相似风格
        var similarSeeds = ragService.search(genreStr + " " + coreConflict, "zh", 3);
        
        // 构建Seed（实际应该调用LLM生成）
        NovelSeed seed = NovelSeed.builder()
                .seedId(UUID.randomUUID().toString())
                .title(generateTitle(genreStr, coreConflict))
                .genre(parseGenre(genreStr))
                .coreConflict(coreConflict)
                .worldSetting(worldSetting)
                .protagonistSetting("待生成主角设定")
                .targetWordCount(1000000) // 100万字
                .createTime(LocalDateTime.now())
                .build();
        
        log.info("Seed生成完成: {}", seed.getSeedId());
        return seed;
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
        // 简化处理，实际应该调用LLM生成
        return genre + "：" + conflict.substring(0, Math.min(10, conflict.length()));
    }
    
}
