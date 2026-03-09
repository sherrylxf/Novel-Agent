package cn.bugstack.novel.infrastructure.adapter.kg;

import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KGGraphDTO;
import cn.bugstack.novel.domain.service.kg.KgCharacterSyncUtil;
import cn.bugstack.novel.domain.service.kg.KgGraphDedupUtil;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Neo4j知识图谱服务实现
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
                    c.abilities = $abilities,
                    c.novelId = $novelId,
                    c += $attributes
                """;
            Map<String, Object> params = new HashMap<>();
            params.put("characterId", character.getCharacterId());
            params.put("name", character.getName() != null ? character.getName() : "");
            params.put("type", character.getType() != null ? character.getType() : "");
            params.put("personality", character.getPersonality() != null ? character.getPersonality() : "");
            params.put("background", character.getBackground() != null ? character.getBackground() : "");
            params.put("abilities", character.getAbilities() != null ? character.getAbilities() : "");
            params.put("novelId", character.getNovelId() != null ? character.getNovelId() : "");
            params.put("attributes", sanitizeProperties(character.getAttributes()));
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
                params.put("properties", sanitizeProperties(properties));
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
                Character.CharacterBuilder b = Character.builder()
                        .characterId(node.get("characterId").asString())
                        .name(getString(node, "name"))
                        .type(getString(node, "type"))
                        .personality(getString(node, "personality"))
                        .background(getString(node, "background"))
                        .abilities(getString(node, "abilities"));
                if (node.containsKey("novelId")) b.novelId(getString(node, "novelId"));
                b.attributes(extractExtraCharacterAttributes(node));
                return b.build();
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
                SET f.content = $content,
                    f.novelId = $novelId,
                    f.resolved = $resolved
                """;
            Map<String, Object> params = new HashMap<>();
            params.put("foreshadowingId", foreshadowingId);
            params.put("content", content);
            params.put("novelId", properties != null && properties.containsKey("novelId") ? properties.get("novelId") : "");
            params.put("resolved", properties != null && properties.containsKey("resolved") ? properties.get("resolved") : false);
            if (properties != null && !properties.isEmpty()) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    if ("novelId".equals(entry.getKey()) || "resolved".equals(entry.getKey())) continue;
                    cypher += ", f." + entry.getKey() + " = $" + entry.getKey();
                    params.put(entry.getKey(), entry.getValue());
                }
            }
            session.run(cypher, params);
            log.info("KG存储: 伏笔已写入知识图谱, foreshadowingId={}, novelId={}", foreshadowingId, params.get("novelId"));
        }
    }
    
    @Override
    public List<String> listUnresolvedForeshadowing(String novelId) {
        if (neo4jDriver == null) return new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MATCH (f:Foreshadowing)
                WHERE f.novelId = $novelId AND (f.resolved IS NULL OR f.resolved = false)
                RETURN f.content AS content
                """;
            var result = session.run(cypher, Values.parameters("novelId", novelId != null ? novelId : ""));
            List<String> list = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                String content = record.get("content").isNull() ? null : record.get("content").asString();
                if (content != null && !content.isEmpty()) list.add(content);
            }
            log.debug("KG查询: 未解决伏笔数量 novelId={}, count={}", novelId, list.size());
            return list;
        }
    }

    @Override
    public List<String> listUnresolvedForeshadowingIds(String novelId) {
        if (neo4jDriver == null) return new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MATCH (f:Foreshadowing)
                WHERE f.novelId = $novelId AND (f.resolved IS NULL OR f.resolved = false)
                RETURN f.foreshadowingId AS id
                """;
            var result = session.run(cypher, Values.parameters("novelId", novelId != null ? novelId : ""));
            List<String> list = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                if (!record.get("id").isNull()) list.add(record.get("id").asString());
            }
            return list;
        }
    }
    
    @Override
    public KGGraphDTO getGraph(String novelId) {
        if (neo4jDriver == null) return KGGraphDTO.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        String nid = novelId != null ? novelId : "";
        List<KGGraphDTO.GraphNode> nodes = new ArrayList<>();
        List<KGGraphDTO.GraphEdge> edges = new ArrayList<>();
        java.util.Set<String> seenNodeIds = new java.util.HashSet<>();
        try (Session session = neo4jDriver.session()) {
            // 1. 人物
            String charCypher = nid.isEmpty()
                ? "MATCH (c:Character) RETURN c"
                : "MATCH (c:Character) WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '') RETURN c";
            var charResult = nid.isEmpty() ? session.run(charCypher) : session.run(charCypher, Values.parameters("novelId", nid));
            while (charResult.hasNext()) {
                try {
                    var record = charResult.next();
                    var node = record.get("c").asNode();
                    if (!node.containsKey("characterId") || node.get("characterId").isNull()) continue;
                    String cid = node.get("characterId").asString();
                    if (cid == null || cid.isBlank()) continue;
                    if (!seenNodeIds.add(cid)) continue;
                    Map<String, Object> props = new HashMap<>();
                    props.put("characterId", cid);
                    props.put("name", getString(node, "name"));
                    props.put("type", getString(node, "type"));
                    props.put("novelId", getString(node, "novelId"));
                    nodes.add(KGGraphDTO.GraphNode.builder()
                            .id(cid)
                            .label(getString(node, "name"))
                            .type("Character")
                            .properties(props)
                            .build());
                } catch (Exception e) {
                    log.warn("跳过无效人物节点: {}", e.getMessage());
                }
            }
            // 2. 伏笔
            String foreCypher = nid.isEmpty() ? "MATCH (f:Foreshadowing) RETURN f" : "MATCH (f:Foreshadowing) WHERE f.novelId = $novelId RETURN f";
            var foreResult = nid.isEmpty() ? session.run(foreCypher) : session.run(foreCypher, Values.parameters("novelId", nid));
            while (foreResult.hasNext()) {
                try {
                    var record = foreResult.next();
                    var node = record.get("f").asNode();
                    if (!node.containsKey("foreshadowingId") || node.get("foreshadowingId").isNull()) continue;
                    String fid = node.get("foreshadowingId").asString();
                    if (fid == null || fid.isBlank()) continue;
                    if (!seenNodeIds.add(fid)) continue;
                    String content = getString(node, "content");
                    String label = (content != null && !content.isEmpty()) ? (content.length() > 20 ? content.substring(0, 20) + "…" : content) : "";
                    Map<String, Object> props = new HashMap<>();
                    props.put("foreshadowingId", fid);
                    props.put("content", content != null ? content : "");
                    props.put("resolved", node.containsKey("resolved") && !node.get("resolved").isNull() && node.get("resolved").asBoolean());
                    nodes.add(KGGraphDTO.GraphNode.builder()
                            .id(fid)
                            .label(label)
                            .type("Foreshadowing")
                            .properties(props)
                            .build());
                } catch (Exception e) {
                    log.warn("跳过无效伏笔节点: {}", e.getMessage());
                }
            }
            // 3. 剧情线程、事件、地点（增加连线来源）
            addEntityNodes(session, nid, "PlotThread", "threadId", "title", nodes, seenNodeIds);
            addEntityNodes(session, nid, "Event", "id", "name", nodes, seenNodeIds);
            addEntityNodes(session, nid, "Location", "id", "name", nodes, seenNodeIds);
            // 4. 所有关系边（跨类型，人物-事件、人物-伏笔/剧情、事件-地点等）
            addAllEdges(session, nid, nodes, edges, seenNodeIds);
            // 5. 故事核心 + 启发式连线（当 Neo4j 无关系时仍能展示连线）；留空 novelId 时用「全局」作为中心
            if (!nodes.isEmpty()) {
                String hubId = nid.isEmpty() ? "hub-global" : "hub-" + nid;
                java.util.Set<String> edgeKeys = new java.util.HashSet<>();
                for (KGGraphDTO.GraphEdge e : edges) {
                    edgeKeys.add(e.getSource() + "->" + e.getTarget());
                }
                if (seenNodeIds.add(hubId)) {
                    Map<String, Object> hubProps = new HashMap<>();
                    hubProps.put("novelId", nid);
                    hubProps.put("label", nid.isEmpty() ? "全局" : "故事核心");
                    nodes.add(0, KGGraphDTO.GraphNode.builder()
                            .id(hubId)
                            .label(nid.isEmpty() ? "全局" : "故事核心")
                            .type("StoryHub")
                            .properties(hubProps)
                            .build());
                }
                int hubEdgeIdx = 0;
                for (KGGraphDTO.GraphNode n : nodes) {
                    if ("StoryHub".equals(n.getType())) continue;
                    String targetId = n.getId();
                    if (targetId == null || targetId.equals(hubId)) continue;
                    if (!edgeKeys.add(hubId + "->" + targetId)) continue;
                    edges.add(KGGraphDTO.GraphEdge.builder()
                            .id("e-hub-" + hubEdgeIdx++)
                            .source(hubId)
                            .target(targetId)
                            .type("CONTAINS")
                            .properties(new HashMap<>())
                            .build());
                }
                // 6. 人物-伏笔启发式连线：伏笔内容包含人物名时建立 MENTIONS 边
                addHeuristicCharacterForeshadowingEdges(nodes, edges);
            }
        } catch (Exception e) {
            log.error("获取知识图谱失败, novelId={}", novelId, e);
            return KGGraphDTO.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
        }
        mergeDuplicateNodes(nodes, edges);
        return KGGraphDTO.builder().nodes(nodes).edges(edges).build();
    }

    /**
     * 按规范键合并重复节点：人物、伏笔、事件、剧情线、地点等，同类型且内容/标题相近的只保留一个。
     */
    private void mergeDuplicateNodes(List<KGGraphDTO.GraphNode> nodes, List<KGGraphDTO.GraphEdge> edges) {
        Map<String, KGGraphDTO.GraphNode> typeKeyToNode = new HashMap<>();
        Map<String, String> duplicateIdToKeptId = new HashMap<>();
        List<KGGraphDTO.GraphNode> result = new ArrayList<>();
        for (KGGraphDTO.GraphNode n : nodes) {
            String type = n.getType() != null ? n.getType() : "";
            String label = n.getLabel() != null ? n.getLabel() : "";
            Map<String, Object> props = n.getProperties() != null ? n.getProperties() : new HashMap<>();
            String canonicalKey = KgGraphDedupUtil.canonicalKeyForNode(type, label, props);
            if (canonicalKey == null || canonicalKey.isEmpty() || "StoryHub".equals(type)) {
                result.add(n);
                continue;
            }
            String groupKey = type + "\t" + canonicalKey;
            KGGraphDTO.GraphNode existing = typeKeyToNode.get(groupKey);
            if (existing == null) {
                String displayLabel = displayLabelForMerged(type, canonicalKey, label, props);
                KGGraphDTO.GraphNode kept = KGGraphDTO.GraphNode.builder()
                        .id(n.getId())
                        .label(displayLabel)
                        .type(type)
                        .properties(new HashMap<>(props))
                        .build();
                if (kept.getProperties() != null) {
                    kept.getProperties().put("name", displayLabel);
                    if ("Foreshadowing".equals(type)) kept.getProperties().put("content", props.get("content") != null ? props.get("content") : displayLabel);
                }
                typeKeyToNode.put(groupKey, kept);
                result.add(kept);
            } else {
                duplicateIdToKeptId.put(n.getId(), existing.getId());
            }
        }
        nodes.clear();
        nodes.addAll(result);
        java.util.Set<String> edgeKeys = new LinkedHashSet<>();
        List<KGGraphDTO.GraphEdge> newEdges = new ArrayList<>();
        for (KGGraphDTO.GraphEdge e : edges) {
            String src = duplicateIdToKeptId.getOrDefault(e.getSource(), e.getSource());
            String tgt = duplicateIdToKeptId.getOrDefault(e.getTarget(), e.getTarget());
            if (src.equals(tgt)) continue;
            String key = src + "->" + tgt;
            if (!edgeKeys.add(key)) continue;
            newEdges.add(KGGraphDTO.GraphEdge.builder()
                    .id("e-" + newEdges.size())
                    .source(src)
                    .target(tgt)
                    .type(e.getType() != null ? e.getType() : "")
                    .properties(e.getProperties() != null ? e.getProperties() : new HashMap<>())
                    .build());
        }
        edges.clear();
        edges.addAll(newEdges);
    }

    private String displayLabelForMerged(String type, String canonicalKey, String originalLabel, Map<String, Object> props) {
        if (canonicalKey == null || canonicalKey.isEmpty()) return originalLabel;
        if ("Foreshadowing".equals(type)) {
            String content = props != null && props.get("content") != null ? String.valueOf(props.get("content")) : canonicalKey;
            return content.length() > 30 ? content.substring(0, 30) + "…" : content;
        }
        return canonicalKey.length() > 25 ? canonicalKey.substring(0, 25) + "…" : canonicalKey;
    }

    private void addEntityNodes(Session session, String nid, String label, String idKey, String nameKey,
                                List<KGGraphDTO.GraphNode> nodes, java.util.Set<String> seenNodeIds) {
        String cypher = nid.isEmpty()
            ? "MATCH (n:" + label + ") RETURN n"
            : "MATCH (n:" + label + ") WHERE (n.novelId = $novelId OR n.novelId IS NULL OR n.novelId = '') RETURN n";
        var result = nid.isEmpty() ? session.run(cypher) : session.run(cypher, Values.parameters("novelId", nid));
        while (result.hasNext()) {
            try {
                var record = result.next();
                var node = record.get("n").asNode();
                if (!node.containsKey(idKey) || node.get(idKey).isNull()) continue;
                String nidVal = node.get(idKey).asString();
                if (nidVal == null || nidVal.isBlank()) continue;
                if (!seenNodeIds.add(nidVal)) continue;
                String name = getString(node, nameKey);
                String displayLabel = (name != null && !name.isEmpty()) ? (name.length() > 15 ? name.substring(0, 15) + "…" : name) : nidVal;
                Map<String, Object> props = new HashMap<>();
                props.put("id", nidVal);
                props.put("name", name != null ? name : "");
                nodes.add(KGGraphDTO.GraphNode.builder()
                        .id(nidVal)
                        .label(displayLabel)
                        .type(label)
                        .properties(props)
                        .build());
            } catch (Exception e) {
                log.warn("跳过无效{}节点: {}", label, e.getMessage());
            }
        }
    }

    private void addAllEdges(Session session, String nid, List<KGGraphDTO.GraphNode> nodes,
                             List<KGGraphDTO.GraphEdge> edges, java.util.Set<String> seenNodeIds) {
        String relCypher = nid.isEmpty()
            ? "MATCH (a)-[r]->(b) WHERE a:Character OR a:Foreshadowing OR a:PlotThread OR a:Event OR a:Location OR a:Faction OR a:Artifact OR a:Technique " +
              "AND (b:Character OR b:Foreshadowing OR b:PlotThread OR b:Event OR b:Location OR b:Faction OR b:Artifact OR b:Technique) " +
              "RETURN a, b, type(r) AS relType"
            : "MATCH (a)-[r]->(b) WHERE (a:Character OR a:Foreshadowing OR a:PlotThread OR a:Event OR a:Location OR a:Faction OR a:Artifact OR a:Technique) " +
              "AND (b:Character OR b:Foreshadowing OR b:PlotThread OR b:Event OR b:Location OR b:Faction OR b:Artifact OR b:Technique) " +
              "AND ($novelId = '' OR ((a.novelId = $novelId OR a.novelId IS NULL OR a.novelId = '') AND (b.novelId = $novelId OR b.novelId IS NULL OR b.novelId = ''))) " +
              "RETURN a, b, type(r) AS relType";
        var result = nid.isEmpty() ? session.run(relCypher) : session.run(relCypher, Values.parameters("novelId", nid));
        int edgeIdx = 0;
        java.util.Set<String> seenEdges = new java.util.HashSet<>();
        while (result.hasNext()) {
            try {
                var record = result.next();
                var a = record.get("a").asNode();
                var b = record.get("b").asNode();
                String fromId = nodeId(a);
                String toId = nodeId(b);
                if (fromId == null || toId == null) continue;
                String edgeKey = fromId + "->" + record.get("relType").asString() + "->" + toId;
                if (!seenEdges.add(edgeKey)) continue;
                if (!seenNodeIds.contains(fromId)) {
                    seenNodeIds.add(fromId);
                    KGGraphDTO.GraphNode fn = buildNodeFromNeo4jNode(a);
                    if (fn != null) nodes.add(fn);
                }
                if (!seenNodeIds.contains(toId)) {
                    seenNodeIds.add(toId);
                    KGGraphDTO.GraphNode tn = buildNodeFromNeo4jNode(b);
                    if (tn != null) nodes.add(tn);
                }
                String relType = record.get("relType").isNull() ? "RELATED_TO" : record.get("relType").asString();
                edges.add(KGGraphDTO.GraphEdge.builder()
                        .id("e-" + edgeIdx++)
                        .source(fromId)
                        .target(toId)
                        .type(relType)
                        .properties(new HashMap<>())
                        .build());
            } catch (Exception e) {
                log.warn("跳过无效关系边: {}", e.getMessage());
            }
        }
    }

    /** 人物-伏笔启发式连线：伏笔内容包含人物名时建立 MENTIONS 边，解决历史数据无关系的问题 */
    private void addHeuristicCharacterForeshadowingEdges(List<KGGraphDTO.GraphNode> nodes, List<KGGraphDTO.GraphEdge> edges) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<KGGraphDTO.GraphNode> chars = nodes.stream().filter(n -> "Character".equals(n.getType())).toList();
        List<KGGraphDTO.GraphNode> fores = nodes.stream().filter(n -> "Foreshadowing".equals(n.getType())).toList();
        if (chars.isEmpty() || fores.isEmpty()) return;
        int idx = 0;
        for (KGGraphDTO.GraphNode c : chars) {
            String charName = extractCharacterName(c);
            if (charName == null || charName.isBlank()) continue;
            for (KGGraphDTO.GraphNode f : fores) {
                String content = extractForeshadowingContent(f);
                if (content == null || !content.contains(charName)) continue;
                String key = c.getId() + "->" + f.getId();
                if (!seen.add(key)) continue;
                edges.add(KGGraphDTO.GraphEdge.builder()
                        .id("e-mentions-" + idx++)
                        .source(c.getId())
                        .target(f.getId())
                        .type("MENTIONS")
                        .properties(new HashMap<>())
                        .build());
            }
        }
    }

    private static String extractCharacterName(KGGraphDTO.GraphNode c) {
        if (c.getLabel() != null && !c.getLabel().isBlank()) {
            String l = c.getLabel();
            if (l.startsWith("主角'") && l.endsWith("'")) return l.substring(3, l.length() - 1);
            if (l.startsWith("主角'") && l.contains("'")) return l.substring(3, l.indexOf("'", 3));
            return l.trim();
        }
        Object name = c.getProperties() != null ? c.getProperties().get("name") : null;
        return name != null ? name.toString().trim() : null;
    }

    private static String extractForeshadowingContent(KGGraphDTO.GraphNode f) {
        Object content = f.getProperties() != null ? f.getProperties().get("content") : null;
        if (content != null && !content.toString().isBlank()) return content.toString();
        return f.getLabel();
    }

    private static String nodeId(org.neo4j.driver.types.Node node) {
        if (node.containsKey("characterId") && !node.get("characterId").isNull())
            return node.get("characterId").asString();
        if (node.containsKey("foreshadowingId") && !node.get("foreshadowingId").isNull())
            return node.get("foreshadowingId").asString();
        if (node.containsKey("threadId") && !node.get("threadId").isNull())
            return node.get("threadId").asString();
        if (node.containsKey("id") && !node.get("id").isNull())
            return node.get("id").asString();
        return null;
    }

    private static KGGraphDTO.GraphNode buildNodeFromNeo4jNode(org.neo4j.driver.types.Node node) {
        String id = nodeId(node);
        if (id == null) return null;
        String labelVal;
        String typeVal;
        if (node.containsKey("characterId")) {
            typeVal = "Character";
            labelVal = node.containsKey("name") && !node.get("name").isNull() ? node.get("name").asString() : id;
        } else if (node.containsKey("foreshadowingId")) {
            typeVal = "Foreshadowing";
            String content = node.containsKey("content") && !node.get("content").isNull() ? node.get("content").asString() : "";
            labelVal = content.length() > 20 ? content.substring(0, 20) + "…" : content;
        } else if (node.containsKey("threadId")) {
            typeVal = "PlotThread";
            labelVal = node.containsKey("title") && !node.get("title").isNull() ? node.get("title").asString() : id;
            if (labelVal != null && labelVal.length() > 15) labelVal = labelVal.substring(0, 15) + "…";
        } else {
            typeVal = node.labels().iterator().next();
            labelVal = node.containsKey("name") && !node.get("name").isNull() ? node.get("name").asString() : id;
            if (labelVal != null && labelVal.length() > 15) labelVal = labelVal.substring(0, 15) + "…";
        }
        Map<String, Object> props = new HashMap<>();
        props.put("id", id);
        return KGGraphDTO.GraphNode.builder().id(id).label(labelVal != null ? labelVal : id).type(typeVal).properties(props).build();
    }

    @Override
    public List<Map<String, Object>> listCharacters(String novelId) {
        if (neo4jDriver == null) return new ArrayList<>();
        String nid = novelId != null ? novelId : "";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            String cypher = nid.isEmpty()
                ? "MATCH (c:Character) RETURN c.characterId AS characterId, c.name AS name, c.type AS type, c.personality AS personality, c.background AS background, c.abilities AS abilities, c.novelId AS novelId"
                : "MATCH (c:Character) WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '') RETURN c.characterId AS characterId, c.name AS name, c.type AS type, c.personality AS personality, c.background AS background, c.abilities AS abilities, c.novelId AS novelId";
            var result = nid.isEmpty() ? session.run(cypher) : session.run(cypher, Values.parameters("novelId", nid));
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> m = new HashMap<>();
                m.put("characterId", record.get("characterId").asString());
                m.put("name", record.get("name").isNull() ? "" : record.get("name").asString());
                m.put("type", record.get("type").isNull() ? "" : record.get("type").asString());
                m.put("personality", record.get("personality").isNull() ? "" : record.get("personality").asString());
                m.put("background", record.get("background").isNull() ? "" : record.get("background").asString());
                m.put("abilities", record.get("abilities").isNull() ? "" : record.get("abilities").asString());
                m.put("novelId", record.get("novelId").isNull() ? "" : record.get("novelId").asString());
                list.add(m);
            }
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> listForeshadowing(String novelId) {
        if (neo4jDriver == null) return new ArrayList<>();
        String nid = novelId != null ? novelId : "";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            String cypher = nid.isEmpty()
                ? "MATCH (f:Foreshadowing) RETURN f.foreshadowingId AS id, f.content AS content, f.resolved AS resolved, f.chapterId AS chapterId"
                : "MATCH (f:Foreshadowing) WHERE f.novelId = $novelId RETURN f.foreshadowingId AS id, f.content AS content, f.resolved AS resolved, f.chapterId AS chapterId";
            var result = nid.isEmpty() ? session.run(cypher) : session.run(cypher, Values.parameters("novelId", nid));
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> m = new HashMap<>();
                m.put("foreshadowingId", record.get("id").asString());
                m.put("content", record.get("content").isNull() ? "" : record.get("content").asString());
                m.put("resolved", !record.get("resolved").isNull() && record.get("resolved").asBoolean());
                m.put("chapterId", record.get("chapterId").isNull() ? null : record.get("chapterId").asString());
                list.add(m);
            }
        }
        return list;
    }

    @Override
    public void deleteCharacter(String characterId) {
        if (neo4jDriver == null) return;
        try (Session session = neo4jDriver.session()) {
            session.run("MATCH (c:Character {characterId: $id}) DETACH DELETE c", Values.parameters("id", characterId));
            log.info("删除人物节点: {}", characterId);
        }
    }

    @Override
    public void deleteForeshadowing(String foreshadowingId) {
        if (neo4jDriver == null) return;
        try (Session session = neo4jDriver.session()) {
            session.run("MATCH (f:Foreshadowing {foreshadowingId: $id}) DELETE f", Values.parameters("id", foreshadowingId));
            log.info("删除伏笔节点: {}", foreshadowingId);
        }
    }

    @Override
    public void updateCharacter(Character character) {
        if (neo4jDriver == null) return;
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MATCH (c:Character {characterId: $characterId})
                SET c.name = $name, c.type = $type, c.personality = $personality, c.background = $background, c.abilities = $abilities, c.novelId = $novelId, c += $attributes
                """;
            Map<String, Object> params = new HashMap<>();
            params.put("characterId", character.getCharacterId());
            params.put("name", character.getName() != null ? character.getName() : "");
            params.put("type", character.getType() != null ? character.getType() : "");
            params.put("personality", character.getPersonality() != null ? character.getPersonality() : "");
            params.put("background", character.getBackground() != null ? character.getBackground() : "");
            params.put("abilities", character.getAbilities() != null ? character.getAbilities() : "");
            params.put("novelId", character.getNovelId() != null ? character.getNovelId() : "");
            params.put("attributes", sanitizeProperties(character.getAttributes()));
            session.run(cypher, params);
            log.info("更新人物节点: {}", character.getCharacterId());
        }
    }

    @Override
    public void updateForeshadowing(String foreshadowingId, String content, Map<String, Object> properties) {
        if (neo4jDriver == null) return;
        try (Session session = neo4jDriver.session()) {
            List<String> sets = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("id", foreshadowingId);
            if (content != null) {
                sets.add("f.content = $content");
                params.put("content", content);
            }
            if (properties != null) {
                if (properties.containsKey("resolved")) {
                    sets.add("f.resolved = $resolved");
                    params.put("resolved", properties.get("resolved"));
                }
                if (properties.containsKey("novelId")) {
                    sets.add("f.novelId = $novelId");
                    params.put("novelId", properties.get("novelId"));
                }
            }
            if (sets.isEmpty()) return;
            String cypher = "MATCH (f:Foreshadowing {foreshadowingId: $id}) SET " + String.join(", ", sets);
            session.run(cypher, params);
            if (properties != null && Boolean.TRUE.equals(properties.get("resolved"))) {
                session.run("MATCH (p:PlotThread)-[:REFERS_TO]->(f:Foreshadowing {foreshadowingId: $id}) SET p.status = 'RESOLVED'", params);
            }
            log.info("更新伏笔节点: {}, resolved={}", foreshadowingId, properties != null && Boolean.TRUE.equals(properties.get("resolved")));
        }
    }

    @Override
    public List<String> listActivePlotThreads(String novelId, int limit) {
        if (neo4jDriver == null) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                MATCH (p:PlotThread)
                WHERE p.novelId = $novelId AND (p.status IS NULL OR p.status <> 'RESOLVED')
                RETURN p.title AS title
                ORDER BY p.updatedAt DESC, p.title ASC
                LIMIT $limit
                """;
            var result = session.run(cypher, Values.parameters(
                    "novelId", novelId != null ? novelId : "",
                    "limit", Math.max(limit, 1)));
            while (result.hasNext()) {
                var record = result.next();
                if (!record.get("title").isNull()) {
                    list.add(record.get("title").asString());
                }
            }
        }
        return list;
    }

    @Override
    public void upsertEntity(String novelId, String entityType, String entityId, String name, Map<String, Object> properties) {
        if (neo4jDriver == null) return;
        String label = sanitizeSymbol(entityType, "Concept");
        String keyProperty = keyPropertyForType(label);
        String cypher = "MERGE (n:" + label + " {" + keyProperty + ": $entityId}) " +
                "SET n.name = $name, n.novelId = $novelId, n += $properties";
        Map<String, Object> params = new HashMap<>();
        params.put("entityId", entityId);
        params.put("name", name != null ? name : "");
        params.put("novelId", novelId != null ? novelId : "");
        params.put("properties", sanitizeProperties(properties));
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, params);
            log.debug("写入知识图谱实体: label={}, id={}, name={}", label, entityId, name);
        }
    }

    @Override
    public void upsertPlotThread(String novelId, String threadId, String title, String status, Map<String, Object> properties) {
        if (neo4jDriver == null) return;
        String cypher = """
            MERGE (p:PlotThread {threadId: $threadId})
            SET p.title = $title,
                p.status = $status,
                p.novelId = $novelId,
                p.updatedAt = datetime(),
                p += $properties
            """;
        Map<String, Object> params = new HashMap<>();
        params.put("threadId", threadId);
        params.put("title", title != null ? title : "");
        params.put("status", status != null ? status : "ACTIVE");
        params.put("novelId", novelId != null ? novelId : "");
        params.put("properties", sanitizeProperties(properties));
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, params);
            log.debug("写入剧情线程: threadId={}, title={}", threadId, title);
        }
    }

    @Override
    public void createEntityRelationship(String fromType, String fromId, String toType, String toId, String relationType, Map<String, Object> properties) {
        if (neo4jDriver == null) return;
        String safeFromType = sanitizeSymbol(fromType, "Concept");
        String safeToType = sanitizeSymbol(toType, "Concept");
        String safeRelationType = sanitizeSymbol(relationType, "RELATED_TO");
        String fromKey = keyPropertyForType(safeFromType);
        String toKey = keyPropertyForType(safeToType);
        String cypher = "MATCH (from:" + safeFromType + " {" + fromKey + ": $fromId}) " +
                "MATCH (to:" + safeToType + " {" + toKey + ": $toId}) " +
                "MERGE (from)-[r:" + safeRelationType + "]->(to) " +
                "SET r += $properties";
        Map<String, Object> params = new HashMap<>();
        params.put("fromId", fromId);
        params.put("toId", toId);
        params.put("properties", sanitizeProperties(properties));
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, params);
        }
    }

    @Override
    public StoryKnowledgeSnapshot buildStoryKnowledge(String novelId, List<String> characterNames, String location, List<String> eventNames, int limit) {
        StoryKnowledgeSnapshot snapshot = StoryKnowledgeSnapshot.builder().build();
        if (neo4jDriver == null) {
            return snapshot;
        }
        List<String> scopedCharacterNames = KgStorySyncUtil.distinctNonBlank(characterNames);
        try (Session session = neo4jDriver.session()) {
            List<String> characterFacts = loadCharacterFacts(session, novelId, scopedCharacterNames, limit);
            List<String> factionFacts = loadFactionFacts(session, novelId, scopedCharacterNames, limit);
            List<String> locationFacts = loadLocationFacts(session, novelId, scopedCharacterNames, location, limit);
            List<String> eventFacts = loadEventFacts(session, novelId, scopedCharacterNames, eventNames, limit);
            snapshot.setCharacterFacts(characterFacts);
            snapshot.setFactionFacts(factionFacts);
            snapshot.setLocationFacts(locationFacts);
            snapshot.setEventFacts(eventFacts);
            snapshot.setWorldFacts(mergeFacts(factionFacts, locationFacts, eventFacts));
            snapshot.setActivePlotThreads(listActivePlotThreads(novelId, limit));
            snapshot.setUnresolvedClues(listUnresolvedForeshadowing(novelId));
            snapshot.setContinuityWarnings(loadContinuityWarnings(session, novelId, scopedCharacterNames, limit));
        } catch (Exception e) {
            log.warn("构建故事知识快照失败，返回空快照", e);
        }
        return snapshot;
    }

    private static String getString(org.neo4j.driver.types.Node node, String key) {
        if (!node.containsKey(key) || node.get(key).isNull()) return "";
        return node.get(key).asString();
    }

    private static Map<String, Object> extractExtraCharacterAttributes(org.neo4j.driver.types.Node node) {
        Map<String, Object> attrs = new HashMap<>();
        java.util.Set<String> reserved = java.util.Set.of(
                "characterId", "name", "type", "personality", "background", "abilities", "novelId");
        for (String key : node.keys()) {
            if (reserved.contains(key) || node.get(key).isNull()) {
                continue;
            }
            attrs.put(key, node.get(key).asObject());
        }
        return attrs;
    }

    private List<String> loadCharacterFacts(Session session, String novelId, List<String> characterNames, int limit) {
        if (characterNames == null || characterNames.isEmpty()) {
            return new ArrayList<>();
        }
        String cypher = """
            MATCH (c:Character)
            WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '')
              AND c.name IN $names
            OPTIONAL MATCH (c)-[charRel]-(otherChar:Character)
            WITH c, collect(DISTINCT type(charRel) + ':' + coalesce(otherChar.name, otherChar.characterId, ''))[0..3] AS charLinks
            OPTIONAL MATCH (c)-[facRel]->(f:Faction)
            WITH c, charLinks, collect(DISTINCT type(facRel) + ':' + coalesce(f.name, f.id, ''))[0..2] AS factionLinks
            OPTIONAL MATCH (c)-[artRel]->(a:Artifact)
            WITH c, charLinks, factionLinks, collect(DISTINCT type(artRel) + ':' + coalesce(a.name, a.id, ''))[0..2] AS artifactLinks
            OPTIONAL MATCH (c)-[tecRel]->(t:Technique)
            WITH c, charLinks, factionLinks, artifactLinks, collect(DISTINCT type(tecRel) + ':' + coalesce(t.name, t.id, ''))[0..2] AS techniqueLinks
            OPTIONAL MATCH (c)-[evtRel]->(e:Event)
            WITH c, charLinks, factionLinks, artifactLinks, techniqueLinks, collect(DISTINCT type(evtRel) + ':' + coalesce(e.name, e.id, ''))[0..2] AS eventLinks
            OPTIONAL MATCH (c)-[locRel]->(l:Location)
            RETURN c.name AS name,
                   c.type AS type,
                   c.personality AS personality,
                   c.background AS background,
                   charLinks,
                   factionLinks,
                   artifactLinks,
                   techniqueLinks,
                   eventLinks,
                   collect(DISTINCT type(locRel) + ':' + coalesce(l.name, l.id, ''))[0..2] AS locationLinks
            LIMIT $limit
            """;
        var result = session.run(cypher, Values.parameters(
                "novelId", novelId != null ? novelId : "",
                "names", characterNames,
                "limit", Math.max(limit, 1)));
        List<String> facts = new ArrayList<>();
        while (result.hasNext()) {
            var record = result.next();
            StringBuilder fact = new StringBuilder();
            fact.append(record.get("name").isNull() ? "" : record.get("name").asString());
            String type = record.get("type").isNull() ? "" : record.get("type").asString();
            String personality = record.get("personality").isNull() ? "" : record.get("personality").asString();
            String background = record.get("background").isNull() ? "" : record.get("background").asString();
            if (!type.isEmpty()) {
                fact.append("（").append(type).append("）");
            }
            if (!personality.isEmpty()) {
                fact.append(" 性格：").append(personality);
            }
            if (!background.isEmpty()) {
                fact.append(" 背景：").append(background.length() > 80 ? background.substring(0, 80) + "..." : background);
            }
            appendFactSection(fact, "人物关系", readLinks(record, "charLinks"));
            appendFactSection(fact, "所属势力", readLinks(record, "factionLinks"));
            appendFactSection(fact, "相关法宝", readLinks(record, "artifactLinks"));
            appendFactSection(fact, "相关功法", readLinks(record, "techniqueLinks"));
            appendFactSection(fact, "参与事件", readLinks(record, "eventLinks"));
            appendFactSection(fact, "活动地点", readLinks(record, "locationLinks"));
            facts.add(fact.toString());
        }
        return facts;
    }

    private List<String> loadLocationFacts(Session session, String novelId, List<String> characterNames, String location, int limit) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        if (location != null && !location.isBlank()) {
            String locationCypher = """
                MATCH (l:Location)
                WHERE l.novelId = $novelId AND l.name = $location
                OPTIONAL MATCH (l)<-[r]-(other)
                RETURN l.name AS name,
                       l.description AS description,
                       collect(DISTINCT type(r) + ':' + coalesce(other.name, other.title, other.characterId, other.id, ''))[0..3] AS links
                LIMIT 1
                """;
            var locationResult = session.run(locationCypher, Values.parameters(
                    "novelId", novelId != null ? novelId : "",
                    "location", location));
            if (locationResult.hasNext()) {
                var record = locationResult.next();
                StringBuilder fact = new StringBuilder("地点：").append(record.get("name").asString());
                String description = record.get("description").isNull() ? "" : record.get("description").asString();
                if (!description.isEmpty()) {
                    fact.append("，").append(description);
                }
                List<String> links = record.get("links").isNull()
                        ? new ArrayList<>()
                        : record.get("links").asList(v -> v.isNull() ? "" : v.asString());
                if (!links.isEmpty()) {
                    fact.append("，关联：").append(String.join("；", links));
                }
                facts.add(fact.toString());
            }
        }
        if (characterNames != null && !characterNames.isEmpty()) {
            String charLocationCypher = """
                MATCH (c:Character)-[r]->(l:Location)
                WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '')
                  AND c.name IN $names
                RETURN l.name AS name,
                       collect(DISTINCT c.name + ':' + type(r))[0..3] AS refs
                LIMIT $limit
                """;
            var result = session.run(charLocationCypher, Values.parameters(
                    "novelId", novelId != null ? novelId : "",
                    "names", characterNames,
                    "limit", Math.max(limit, 1)));
            while (result.hasNext()) {
                var record = result.next();
                if (record.get("name").isNull()) continue;
                StringBuilder fact = new StringBuilder("地点：").append(record.get("name").asString());
                appendFactSection(fact, "相关人物", readLinks(record, "refs"));
                facts.add(fact.toString());
            }
        }
        return new ArrayList<>(facts);
    }

    private List<String> loadEventFacts(Session session, String novelId, List<String> characterNames, List<String> eventNames, int limit) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        if (eventNames != null && !eventNames.isEmpty()) {
            String eventCypher = """
                MATCH (e:Event)
                WHERE e.novelId = $novelId AND e.name IN $eventNames
                OPTIONAL MATCH (c:Character)-[r]->(e)
                OPTIONAL MATCH (e)-[:OCCURS_AT]->(l:Location)
                RETURN e.name AS name,
                       e.eventType AS eventType,
                       e.summary AS summary,
                       e.chapterNumber AS chapterNumber,
                       collect(DISTINCT c.name + ':' + type(r))[0..3] AS characters,
                       collect(DISTINCT l.name)[0..2] AS locations
                LIMIT $limit
                """;
            var eventResult = session.run(eventCypher, Values.parameters(
                    "novelId", novelId != null ? novelId : "",
                    "eventNames", eventNames,
                    "limit", Math.max(limit, 1)));
            while (eventResult.hasNext()) {
                var record = eventResult.next();
                StringBuilder fact = new StringBuilder("事件：").append(record.get("name").asString());
                if (!record.get("eventType").isNull() && !record.get("eventType").asString().isBlank()) {
                    fact.append("【").append(record.get("eventType").asString()).append("】");
                }
                if (!record.get("chapterNumber").isNull()) {
                    fact.append("（第").append(record.get("chapterNumber").asInt()).append("章）");
                }
                if (!record.get("summary").isNull() && !record.get("summary").asString().isBlank()) {
                    fact.append("，").append(record.get("summary").asString());
                }
                appendFactSection(fact, "关联人物", readLinks(record, "characters"));
                appendFactSection(fact, "发生地点", readLinks(record, "locations"));
                facts.add(fact.toString());
            }
        }
        if (characterNames != null && !characterNames.isEmpty()) {
            String recentEventCypher = """
                MATCH (c:Character)-[r]->(e:Event)
                WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '')
                  AND c.name IN $names
                RETURN e.name AS name,
                       e.eventType AS eventType,
                       e.summary AS summary,
                       collect(DISTINCT c.name + ':' + type(r))[0..3] AS refs
                LIMIT $limit
                """;
            var recentEventResult = session.run(recentEventCypher, Values.parameters(
                    "novelId", novelId != null ? novelId : "",
                    "names", characterNames,
                    "limit", Math.max(limit, 1)));
            while (recentEventResult.hasNext()) {
                var record = recentEventResult.next();
                if (record.get("name").isNull()) continue;
                StringBuilder fact = new StringBuilder("事件：").append(record.get("name").asString());
                if (!record.get("eventType").isNull() && !record.get("eventType").asString().isBlank()) {
                    fact.append("【").append(record.get("eventType").asString()).append("】");
                }
                if (!record.get("summary").isNull() && !record.get("summary").asString().isBlank()) {
                    fact.append("，").append(record.get("summary").asString());
                }
                appendFactSection(fact, "涉及人物", readLinks(record, "refs"));
                facts.add(fact.toString());
            }
        }
        return new ArrayList<>(facts);
    }

    private List<String> loadFactionFacts(Session session, String novelId, List<String> characterNames, int limit) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        if (characterNames != null && !characterNames.isEmpty()) {
            String conceptCypher = """
                MATCH (c:Character)-[r]->(entity)
                WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '')
                  AND c.name IN $names
                  AND (entity:Faction OR entity:Artifact OR entity:Technique)
                RETURN labels(entity)[0] AS entityType,
                       coalesce(entity.name, entity.id) AS entityName,
                       collect(DISTINCT c.name + ':' + type(r))[0..3] AS refs
                LIMIT $limit
                """;
            var conceptResult = session.run(conceptCypher, Values.parameters(
                    "novelId", novelId != null ? novelId : "",
                    "names", characterNames,
                    "limit", Math.max(limit * 2, 4)));
            while (conceptResult.hasNext()) {
                var record = conceptResult.next();
                String entityType = record.get("entityType").isNull() ? "Concept" : record.get("entityType").asString();
                String entityName = record.get("entityName").isNull() ? "" : record.get("entityName").asString();
                if (entityName.isBlank()) {
                    continue;
                }
                String fact = typeToChinese(entityType) + "：" + entityName
                        + (readLinks(record, "refs").isEmpty() ? "" : " 关联人物：" + String.join("；", readLinks(record, "refs")));
                facts.add(fact);
            }
        }
        return new ArrayList<>(facts);
    }

    private List<String> loadContinuityWarnings(Session session, String novelId, List<String> characterNames, int limit) {
        List<String> warnings = new ArrayList<>();
        if (characterNames == null || characterNames.isEmpty()) {
            return warnings;
        }
        String cypher = """
            MATCH (c:Character)
            WHERE (c.novelId = $novelId OR c.novelId IS NULL OR c.novelId = '')
              AND c.name IN $names
            RETURN c.name AS name, c.status AS status, c.goal AS goal
            LIMIT $limit
            """;
        var result = session.run(cypher, Values.parameters(
                "novelId", novelId != null ? novelId : "",
                "names", characterNames,
                "limit", Math.max(limit * 2, 4)));
        while (result.hasNext()) {
            var record = result.next();
            String name = record.get("name").isNull() ? "" : record.get("name").asString();
            String status = record.get("status").isNull() ? "" : record.get("status").asString();
            String goal = record.get("goal").isNull() ? "" : record.get("goal").asString();
            if ("DEAD".equalsIgnoreCase(status)) {
                warnings.add("人物 " + name + " 已标记为死亡，避免继续以正常状态登场");
            }
            if (!goal.isBlank()) {
                warnings.add("人物 " + name + " 当前目标：" + goal);
            }
        }
        return warnings;
    }

    private List<String> mergeFacts(List<String>... parts) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (parts != null) {
            for (List<String> part : parts) {
                if (part != null) {
                    merged.addAll(part);
                }
            }
        }
        return new ArrayList<>(merged);
    }

    private static List<String> readLinks(org.neo4j.driver.Record record, String key) {
        if (record == null || key == null || record.get(key).isNull()) {
            return new ArrayList<>();
        }
        List<String> values = record.get(key).asList(v -> v.isNull() ? "" : v.asString());
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !value.endsWith(":")) {
                cleaned.add(value);
            }
        }
        return cleaned;
    }

    private static void appendFactSection(StringBuilder fact, String title, List<String> links) {
        if (fact == null || links == null || links.isEmpty()) {
            return;
        }
        fact.append(" ").append(title).append("：").append(String.join("；", links));
    }

    private static String typeToChinese(String entityType) {
        if ("Faction".equals(entityType)) return "势力";
        if ("Artifact".equals(entityType)) return "法宝";
        if ("Technique".equals(entityType)) return "功法";
        return entityType != null ? entityType : "概念";
    }

    private static String keyPropertyForType(String entityType) {
        if ("Character".equalsIgnoreCase(entityType)) return "characterId";
        if ("Foreshadowing".equalsIgnoreCase(entityType)) return "foreshadowingId";
        if ("PlotThread".equalsIgnoreCase(entityType)) return "threadId";
        if ("Location".equalsIgnoreCase(entityType)) return "id";
        if ("Event".equalsIgnoreCase(entityType)) return "id";
        if ("Faction".equalsIgnoreCase(entityType)) return "id";
        if ("Artifact".equalsIgnoreCase(entityType)) return "id";
        if ("Technique".equalsIgnoreCase(entityType)) return "id";
        return "id";
    }

    private static String sanitizeSymbol(String raw, String fallback) {
        String base = raw != null ? raw.replaceAll("[^A-Za-z0-9_]", "") : "";
        if (base.isEmpty()) {
            return fallback;
        }
        return base;
    }

    private static Map<String, Object> sanitizeProperties(Map<String, Object> properties) {
        Map<String, Object> sanitized = new HashMap<>();
        if (properties == null || properties.isEmpty()) {
            return sanitized;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized;
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

    @Override
    public boolean isConnected() {
        if (neo4jDriver == null) return false;
        try (Session session = neo4jDriver.session()) {
            var result = session.run("RETURN 1 AS n");
            return result.hasNext() && result.single().get("n").asInt() == 1;
        } catch (Exception e) {
            log.warn("Neo4j 连接检查失败: {}", e.getMessage());
            return false;
        }
    }
    
}
