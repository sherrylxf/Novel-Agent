package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KgCharacterSyncUtil;
import cn.bugstack.novel.domain.service.persistence.INovelGenerationStoreService;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * Seed执行节点
 * 生成小说Seed，并将主角同步到知识图谱，避免图谱为空
 */
@Slf4j
@Component
public class SeedExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private INovelGenerationStoreService generationStoreService;
    
    @Autowired(required = false)
    private IKnowledgeGraphService knowledgeGraphService;
    
    @Resource
    private PlanExecuteNode planExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("Seed节点：开始生成Seed");
        
        String genre = context.getAttribute("genre");
        String coreConflict = context.getAttribute("coreConflict");
        String worldSetting = context.getAttribute("worldSetting");
        
        IAgent<String, NovelSeed> seedAgent = orchestrator.getAgent("NovelSeedAgent");
        String input = String.format("题材: %s\n核心冲突: %s\n世界观: %s", genre, coreConflict, worldSetting);
        NovelSeed seed = seedAgent.execute(input, context);
        
        context.setAttribute("seed", seed);
        
        try {
            generationStoreService.persistSeed(context.getNovelId(), seed);
        } catch (Exception e) {
            log.warn("Seed落库失败，但继续执行，novelId: {}, err: {}", context.getNovelId(), e.getMessage());
        }
        
        // 从主角设定解析主角并写入知识图谱，丰富人物节点
        if (knowledgeGraphService != null && seed != null && seed.getProtagonistSetting() != null && !seed.getProtagonistSetting().isEmpty()) {
            try {
                String novelId = context.getNovelId() != null ? context.getNovelId() : "";
                String name = KgCharacterSyncUtil.parseProtagonistName(seed.getProtagonistSetting());
                String canonical = KgCharacterSyncUtil.canonicalName(name);
                String characterId = KgCharacterSyncUtil.toCharacterId(novelId, name);
                Character protagonist = Character.builder()
                        .characterId(characterId)
                        .name(canonical != null && !canonical.isEmpty() ? canonical : name)
                        .novelId(novelId)
                        .type("主角")
                        .personality("")
                        .background(seed.getProtagonistSetting())
                        .abilities("")
                        .build();
                knowledgeGraphService.createCharacter(protagonist);
                log.info("KG存储: 主角已写入知识图谱, novelId={}, name={}", novelId, name);
            } catch (Exception e) {
                log.warn("主角写入知识图谱失败，不阻塞流程，err: {}", e.getMessage());
            }
        }
        
        context.setCurrentStage(GenerationStage.NOVEL_PLAN.name());
        
        return planExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return planExecuteNode;
    }
    
}
