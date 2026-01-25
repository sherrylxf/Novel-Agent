package cn.bugstack.novel.domain.agent.impl.validation;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识图谱规则校验Agent
 * 校验生成内容是否违反世界规则
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class KGRuleValidatorAgent extends AbstractAgent<Scene, Boolean> {
    
    private final IKnowledgeGraphService kgService;
    
    public KGRuleValidatorAgent(IKnowledgeGraphService kgService) {
        super(AgentType.KG_RULE_VALIDATOR);
        this.kgService = kgService;
    }
    
    @Override
    protected Boolean doExecute(Scene scene, NovelContext context) {
        log.info("开始KG规则校验，场景: {}", scene.getSceneTitle());
        
        // 校验规则1：人物关系是否合理
        String relationshipRule = buildRelationshipRule(scene);
        if (!kgService.validateRule(relationshipRule)) {
            log.warn("人物关系规则校验失败");
            return false;
        }
        
        // 校验规则2：世界规则是否违反
        String worldRule = buildWorldRule(scene);
        if (!kgService.validateRule(worldRule)) {
            log.warn("世界规则校验失败");
            return false;
        }
        
        log.info("KG规则校验通过");
        return true;
    }
    
    private String buildRelationshipRule(Scene scene) {
        // 构建Cypher查询规则
        // 例如：检查场景中的人物关系是否合理
        return "MATCH (c:Character) WHERE c.name IN " + 
               java.util.Arrays.toString(scene.getCharacters()) + 
               " RETURN count(c) > 0";
    }
    
    private String buildWorldRule(Scene scene) {
        // 构建世界规则校验查询
        // 例如：检查修为等级是否合理
        return "MATCH (c:Character)-[:HAS_ABILITY]->(a:Ability) RETURN count(a) > 0";
    }
    
}
