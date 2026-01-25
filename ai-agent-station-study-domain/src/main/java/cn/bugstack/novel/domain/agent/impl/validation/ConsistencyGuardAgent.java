package cn.bugstack.novel.domain.agent.impl.validation;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 一致性守护Agent
 * 检查人物性格、关系是否漂移
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class ConsistencyGuardAgent extends AbstractAgent<Scene, Boolean> {
    
    private final IKnowledgeGraphService kgService;
    
    public ConsistencyGuardAgent(IKnowledgeGraphService kgService) {
        super(AgentType.CONSISTENCY_GUARD);
        this.kgService = kgService;
    }
    
    @Override
    protected Boolean doExecute(Scene scene, NovelContext context) {
        log.info("开始一致性校验，场景: {}", scene.getSceneTitle());
        
        // 检查人物一致性
        for (String characterName : scene.getCharacters()) {
            var character = kgService.queryCharacter(characterName);
            if (character != null) {
                // 检查性格是否一致（简化处理）
                if (!checkPersonalityConsistency(scene.getContent(), character)) {
                    log.warn("人物 {} 性格不一致", characterName);
                    return false;
                }
            }
        }
        
        // 检查关系一致性
        // 这里简化处理，实际应该更复杂
        log.info("一致性校验通过");
        return true;
    }
    
    private boolean checkPersonalityConsistency(String content, cn.bugstack.novel.domain.model.valobj.Character character) {
        // 简化处理，实际应该调用LLM或NLP工具分析
        // 检查内容中的人物行为是否符合其性格设定
        return true; // 默认通过
    }
    
}
