package cn.bugstack.novel.domain.agent.impl.extraction;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.ExtractedEntities;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 信息抽取 Agent
 * 从章节梗概与场景正文中抽取：人物、地点、势力、法宝、功法、事件、关系、伏笔
 * 供 KG 与 PlotTracker 自动更新
 */
@Slf4j
@Component
public class InfoExtractionAgent extends AbstractAgent<Object[], ExtractedEntities> {

    private static final String EXTRACTION_JSON_SCHEMA = """
        {
          "characters": ["正文中出现的所有人物名，含主角/配角/势力代表等具体人名"],
          "locations": ["新出现的地点"],
          "factions": ["势力/宗门/组织名"],
          "artifacts": ["法宝/神器/道具名"],
          "techniques": ["功法/武技/法术名"],
          "events": [{
            "name": "原子剧情事件名称",
            "eventType": "CONFLICT/DISCOVERY/DIALOGUE/BATTLE/REVELATION等",
            "summary": "事件摘要",
            "location": "事件发生地点",
            "outcome": "事件结果",
            "importance": 0.0,
            "participants": [{"name":"人物或势力名","entityType":"Character/Faction","role":"参与角色","outcome":"结果"}],
            "factions": ["相关势力"],
            "relatedThreads": ["相关剧情线"]
          }],
          "relations": [{"subject":"人物A","relationType":"师徒/仇敌/同盟等","object":"人物B或势力"}],
          "foreshadowing": ["本章新埋伏笔或推进的剧情线"],
          "stateChanges": [{"entityType":"Character/Faction/Location","name":"实体名","field":"status/goal/control等","oldValue":"旧值","newValue":"新值","reason":"变化原因"}],
          "plotThreadSignals": [{"threadTitle":"剧情线标题","signalType":"OPEN/ADVANCE/RESOLVE/ABANDON","evidence":"证据","relatedEvent":"关联事件名","summary":"推进摘要"}],
          "aliases": [{"canonical":"规范名","alias":"别名/称谓"}]
        }
        """;

    private final ILLMClient llmClient;

    public InfoExtractionAgent(ILLMClient llmClient) {
        super(AgentType.INFO_EXTRACTION);
        this.llmClient = llmClient;
    }

    @Override
    protected ExtractedEntities doExecute(Object[] input, NovelContext context) {
        if (input == null || input.length < 2) {
            log.warn("信息抽取Agent输入不足，返回空实体");
            return ExtractedEntities.builder().build();
        }
        ChapterOutline outline = input[0] instanceof ChapterOutline ? (ChapterOutline) input[0] : null;
        Scene scene = input[1] instanceof Scene ? (Scene) input[1] : null;
        if (scene == null || scene.getContent() == null || scene.getContent().isBlank()) {
            log.debug("场景内容为空，跳过抽取");
            return ExtractedEntities.builder().build();
        }

        try {
            String systemPrompt = "你是一个专业的小说信息抽取助手。从给定的章节梗概和场景正文中，抽取结构化实体。"
                    + "人物(characters)：抽取正文中出现的所有人物名（含主角、配角、势力代表、对手、盟友、路人等），务必列举具体人名。"
                    + "events：拆成 1-3 个原子剧情事件，每个事件补齐参与者、地点、结果、相关剧情线。"
                    + "stateChanges：抽取人物/势力/地点的重要状态变化。"
                    + "plotThreadSignals：识别剧情线是 OPEN、ADVANCE、RESOLVE 还是 ABANDON。"
                    + "aliases：只在正文出现称谓与本名映射时输出。"
                    + "只抽取文中明确出现或可明确推断的内容，不要编造。空数组用[]表示。";

            String contentExcerpt = scene.getContent();
            if (contentExcerpt.length() > 2500) {
                contentExcerpt = contentExcerpt.substring(0, 2500) + "...";
            }
            String chapterTitle = outline != null ? outline.getChapterTitle() : "";
            String outlineText = outline != null ? outline.getOutline() : "";

            String userPrompt = "章节标题：" + chapterTitle + "\n"
                    + "章节梗概：" + outlineText + "\n\n"
                    + "场景正文（节选前2500字）：\n" + contentExcerpt + "\n\n"
                    + "请严格按照以下 JSON 格式输出，不要添加任何额外文字：\n" + EXTRACTION_JSON_SCHEMA;

            String response = llmClient != null ? llmClient.call(systemPrompt, userPrompt) : "{}";
            ExtractedEntities entities = parseExtractionResponse(response);
            log.info("信息抽取完成: 人物={}, 地点={}, 势力={}, 法宝={}, 功法={}, 事件={}, 关系={}, 状态变化={}, 剧情线信号={}",
                    entities.getCharacters().size(), entities.getLocations().size(), entities.getFactions().size(),
                    entities.getArtifacts().size(), entities.getTechniques().size(), entities.getEvents().size(),
                    entities.getRelations().size(), entities.getStateChanges().size(), entities.getPlotThreadSignals().size());
            return entities;
        } catch (Exception e) {
            log.warn("信息抽取失败，返回空实体: {}", e.getMessage());
            return ExtractedEntities.builder().build();
        }
    }

    private ExtractedEntities parseExtractionResponse(String response) {
        ExtractedEntities.ExtractedEntitiesBuilder builder = ExtractedEntities.builder();
        try {
            String jsonStr = extractJsonFromResponse(response);
            JSONObject json = JSON.parseObject(jsonStr);
            builder.characters(parseStringArray(json, "characters"));
            builder.locations(parseStringArray(json, "locations"));
            builder.factions(parseStringArray(json, "factions"));
            builder.artifacts(parseStringArray(json, "artifacts"));
            builder.techniques(parseStringArray(json, "techniques"));
            builder.events(parseEvents(json, "events"));
            builder.foreshadowing(parseStringArray(json, "foreshadowing"));
            builder.relations(parseRelationTriples(json, "relations"));
            builder.stateChanges(parseStateChanges(json, "stateChanges"));
            builder.plotThreadSignals(parsePlotThreadSignals(json, "plotThreadSignals"));
            builder.aliases(parseAliases(json, "aliases"));
        } catch (Exception e) {
            log.debug("解析抽取结果失败: {}", e.getMessage());
        }
        return builder.build();
    }

    private List<String> parseStringArray(JSONObject json, String key) {
        List<String> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (arr instanceof JSONArray) {
            JSONArray ja = (JSONArray) arr;
            for (int i = 0; i < ja.size(); i++) {
                String s = ja.getString(i);
                if (s != null && !s.trim().isEmpty()) list.add(s.trim());
            }
        }
        return list;
    }

    private List<ExtractedEntities.RelationTriple> parseRelationTriples(JSONObject json, String key) {
        List<ExtractedEntities.RelationTriple> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (!(arr instanceof JSONArray)) return list;
        JSONArray ja = (JSONArray) arr;
        for (int i = 0; i < ja.size(); i++) {
            JSONObject obj = ja.getJSONObject(i);
            if (obj == null) continue;
            String sub = obj.getString("subject");
            String rel = obj.getString("relationType");
            String objVal = obj.getString("object");
            if ((sub != null && !sub.isBlank()) && (rel != null && !rel.isBlank()) && (objVal != null && !objVal.isBlank())) {
                list.add(ExtractedEntities.RelationTriple.builder()
                        .subject(sub.trim())
                        .relationType(rel.trim())
                        .object(objVal.trim())
                        .build());
            }
        }
        return list;
    }

    private List<ExtractedEntities.EventRecord> parseEvents(JSONObject json, String key) {
        List<ExtractedEntities.EventRecord> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (!(arr instanceof JSONArray)) return list;
        JSONArray ja = (JSONArray) arr;
        for (int i = 0; i < ja.size(); i++) {
            JSONObject obj = ja.getJSONObject(i);
            if (obj == null) continue;
            String name = obj.getString("name");
            if (name == null || name.isBlank()) continue;
            list.add(ExtractedEntities.EventRecord.builder()
                    .name(name.trim())
                    .eventType(trimOrEmpty(obj.getString("eventType")))
                    .summary(trimOrEmpty(obj.getString("summary")))
                    .location(trimOrEmpty(obj.getString("location")))
                    .outcome(trimOrEmpty(obj.getString("outcome")))
                    .importance(parseDouble(obj.get("importance")))
                    .participants(parseParticipants(obj, "participants"))
                    .factions(parseStringArray(obj, "factions"))
                    .relatedThreads(parseStringArray(obj, "relatedThreads"))
                    .build());
        }
        return list;
    }

    private List<ExtractedEntities.EventParticipant> parseParticipants(JSONObject json, String key) {
        List<ExtractedEntities.EventParticipant> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (!(arr instanceof JSONArray)) return list;
        JSONArray ja = (JSONArray) arr;
        for (int i = 0; i < ja.size(); i++) {
            JSONObject obj = ja.getJSONObject(i);
            if (obj == null) continue;
            String name = obj.getString("name");
            if (name == null || name.isBlank()) continue;
            list.add(ExtractedEntities.EventParticipant.builder()
                    .name(name.trim())
                    .entityType(trimOrEmpty(obj.getString("entityType")))
                    .role(trimOrEmpty(obj.getString("role")))
                    .outcome(trimOrEmpty(obj.getString("outcome")))
                    .build());
        }
        return list;
    }

    private List<ExtractedEntities.StateChange> parseStateChanges(JSONObject json, String key) {
        List<ExtractedEntities.StateChange> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (!(arr instanceof JSONArray)) return list;
        JSONArray ja = (JSONArray) arr;
        for (int i = 0; i < ja.size(); i++) {
            JSONObject obj = ja.getJSONObject(i);
            if (obj == null) continue;
            String entityType = trimOrEmpty(obj.getString("entityType"));
            String name = trimOrEmpty(obj.getString("name"));
            String field = trimOrEmpty(obj.getString("field"));
            if (entityType.isEmpty() || name.isEmpty() || field.isEmpty()) continue;
            list.add(ExtractedEntities.StateChange.builder()
                    .entityType(entityType)
                    .name(name)
                    .field(field)
                    .oldValue(trimOrEmpty(obj.getString("oldValue")))
                    .newValue(trimOrEmpty(obj.getString("newValue")))
                    .reason(trimOrEmpty(obj.getString("reason")))
                    .build());
        }
        return list;
    }

    private List<ExtractedEntities.PlotThreadSignal> parsePlotThreadSignals(JSONObject json, String key) {
        List<ExtractedEntities.PlotThreadSignal> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (!(arr instanceof JSONArray)) return list;
        JSONArray ja = (JSONArray) arr;
        for (int i = 0; i < ja.size(); i++) {
            JSONObject obj = ja.getJSONObject(i);
            if (obj == null) continue;
            String threadTitle = trimOrEmpty(obj.getString("threadTitle"));
            if (threadTitle.isEmpty()) continue;
            list.add(ExtractedEntities.PlotThreadSignal.builder()
                    .threadTitle(threadTitle)
                    .signalType(trimOrEmpty(obj.getString("signalType")))
                    .evidence(trimOrEmpty(obj.getString("evidence")))
                    .relatedEvent(trimOrEmpty(obj.getString("relatedEvent")))
                    .summary(trimOrEmpty(obj.getString("summary")))
                    .build());
        }
        return list;
    }

    private List<ExtractedEntities.AliasRecord> parseAliases(JSONObject json, String key) {
        List<ExtractedEntities.AliasRecord> list = new ArrayList<>();
        if (json == null || !json.containsKey(key)) return list;
        Object arr = json.get(key);
        if (!(arr instanceof JSONArray)) return list;
        JSONArray ja = (JSONArray) arr;
        for (int i = 0; i < ja.size(); i++) {
            JSONObject obj = ja.getJSONObject(i);
            if (obj == null) continue;
            String canonical = trimOrEmpty(obj.getString("canonical"));
            String alias = trimOrEmpty(obj.getString("alias"));
            if (canonical.isEmpty() || alias.isEmpty()) continue;
            list.add(ExtractedEntities.AliasRecord.builder()
                    .canonical(canonical)
                    .alias(alias)
                    .build());
        }
        return list;
    }

    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "{}";
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (Exception ignore) {
            return null;
        }
    }
}
