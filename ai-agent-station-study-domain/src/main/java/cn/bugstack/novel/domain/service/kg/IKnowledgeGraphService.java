package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.valobj.Character;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public interface IKnowledgeGraphService {
    
    /**
     * 创建人物节点
     */
    void createCharacter(Character character);
    
    /**
     * 创建关系
     *
     * @param fromId 起始节点ID
     * @param toId 目标节点ID
     * @param relationType 关系类型（CP、仇敌、师徒等）
     * @param properties 关系属性
     */
    void createRelationship(String fromId, String toId, String relationType, Map<String, Object> properties);
    
    /**
     * 查询人物关系
     */
    List<Map<String, Object>> queryRelationships(String characterId, String relationType);
    
    /**
     * 查询人物信息
     */
    Character queryCharacter(String characterId);
    
    /**
     * 创建伏笔节点
     */
    void createForeshadowing(String foreshadowingId, String content, Map<String, Object> properties);
    
    /**
     * 校验规则
     *
     * @param rule 规则Cypher查询
     * @return 是否违反规则
     */
    boolean validateRule(String rule);
    
}
