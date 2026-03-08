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
          "events": ["关键事件简要描述"],
          "relations": [{"subject":"人物A","relationType":"师徒/仇敌/同盟等","object":"人物B或势力"}],
          "foreshadowing": ["本章新埋伏笔或推进的剧情线"]
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
            log.info("信息抽取完成: 人物={}, 地点={}, 势力={}, 法宝={}, 功法={}, 关系={}, 伏笔={}",
                    entities.getCharacters().size(), entities.getLocations().size(), entities.getFactions().size(),
                    entities.getArtifacts().size(), entities.getTechniques().size(), entities.getRelations().size(),
                    entities.getForeshadowing().size());
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
            builder.events(parseStringArray(json, "events"));
            builder.foreshadowing(parseStringArray(json, "foreshadowing"));
            builder.relations(parseRelationTriples(json, "relations"));
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

    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "{}";
    }
}
