package cn.bugstack.novel.infrastructure.adapter.kg;

import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j知识图谱服务实现
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(Driver.class)
public class Neo4jKnowledgeGraphService implements IKnowledgeGraphService {
    
    @Autowired(required = false)
    private Driver neo4jDriver;
    
    @Override
    public void createCharacter(Character character) {
        if (neo4jDriver == null) {
            log.warn("Neo4j Driver未配置，无法创建人物节点");
            return;
        }
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MERGE (c:Character {characterId: $characterId})
                SET c.name = $name,
                    c.type = $type,
                    c.personality = $personality,
                    c.background = $background,
                    c.abilities = $abilities
                """;
            
            Map<String, Object> params = new HashMap<>();
            params.put("characterId", character.getCharacterId());
            params.put("name", character.getName());
            params.put("type", character.getType());
            params.put("personality", character.getPersonality());
            params.put("background", character.getBackground());
            params.put("abilities", character.getAbilities());
            
            session.run(cypher, params);
            log.info("创建人物节点: {}", character.getName());
        }
    }
    
    @Override
    public void createRelationship(String fromId, String toId, String relationType, Map<String, Object> properties) {
        if (neo4jDriver == null) {
            log.warn("Neo4j Driver未配置，无法创建关系");
            return;
        }
        try (Session session = neo4jDriver.session()) {
            StringBuilder cypher = new StringBuilder();
            cypher.append("MATCH (from:Character {characterId: $fromId}) ");
            cypher.append("MATCH (to:Character {characterId: $toId}) ");
            cypher.append("MERGE (from)-[r:").append(relationType).append("]->(to) ");
            
            if (properties != null && !properties.isEmpty()) {
                cypher.append("SET r += $properties ");
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("fromId", fromId);
            params.put("toId", toId);
            if (properties != null && !properties.isEmpty()) {
                params.put("properties", properties);
            }
            
            session.run(cypher.toString(), params);
            log.info("创建关系: {} -[{}]-> {}", fromId, relationType, toId);
        }
    }
    
    @Override
    public List<Map<String, Object>> queryRelationships(String characterId, String relationType) {
        if (neo4jDriver == null) {
            log.warn("Neo4j Driver未配置，无法查询关系");
            return new ArrayList<>();
        }
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MATCH (c:Character {characterId: $characterId})-[r:%s]->(other:Character)
                RETURN other.characterId as characterId, other.name as name, type(r) as relationType, r as properties
                """.formatted(relationType);
            
            var result = session.run(cypher, Values.parameters("characterId", characterId));
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> rel = new HashMap<>();
                rel.put("characterId", record.get("characterId").asString());
                rel.put("name", record.get("name").asString());
                rel.put("relationType", record.get("relationType").asString());
                relationships.add(rel);
            }
            
            return relationships;
        }
    }
    
    @Override
    public Character queryCharacter(String characterId) {
        if (neo4jDriver == null) {
            log.warn("Neo4j Driver未配置，无法查询人物");
            return null;
        }
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MATCH (c:Character {characterId: $characterId})
                RETURN c
                """;
            
            var result = session.run(cypher, Values.parameters("characterId", characterId));
            
            if (result.hasNext()) {
                var record = result.next();
                var node = record.get("c").asNode();
                
                return Character.builder()
                        .characterId(node.get("characterId").asString())
                        .name(node.get("name").asString())
                        .type(node.get("type").asString())
                        .personality(node.get("personality").asString())
                        .background(node.get("background").asString())
                        .abilities(node.get("abilities").asString())
                        .build();
            }
            
            return null;
        }
    }
    
    @Override
    public void createForeshadowing(String foreshadowingId, String content, Map<String, Object> properties) {
        if (neo4jDriver == null) {
            log.warn("Neo4j Driver未配置，无法创建伏笔");
            return;
        }
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MERGE (f:Foreshadowing {foreshadowingId: $foreshadowingId})
                SET f.content = $content
                """;
            
            Map<String, Object> params = new HashMap<>();
            params.put("foreshadowingId", foreshadowingId);
            params.put("content", content);
            
            if (properties != null && !properties.isEmpty()) {
                // 动态添加属性
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    cypher += ", f." + entry.getKey() + " = $" + entry.getKey();
                    params.put(entry.getKey(), entry.getValue());
                }
            }
            
            session.run(cypher, params);
            log.info("创建伏笔节点: {}", foreshadowingId);
        }
    }
    
    @Override
    public boolean validateRule(String rule) {
        if (neo4jDriver == null) {
            log.warn("Neo4j Driver未配置，无法校验规则");
            return false;
        }
        try (Session session = neo4jDriver.session()) {
            var result = session.run(rule);
            
            if (result.hasNext()) {
                var record = result.next();
                // 假设规则查询返回布尔值
                return record.values().stream()
                        .anyMatch(value -> value.asBoolean());
            }
            
            return false;
        } catch (Exception e) {
            log.error("规则校验失败", e);
            return false;
        }
    }
    
}
