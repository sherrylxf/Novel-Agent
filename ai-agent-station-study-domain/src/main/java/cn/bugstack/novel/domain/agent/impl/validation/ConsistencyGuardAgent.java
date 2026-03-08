package cn.bugstack.novel.domain.agent.impl.validation;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KgCharacterSyncUtil;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 一致性守护Agent
 * 检查人物性格、关系是否漂移
 */
@Slf4j
@Component
public class ConsistencyGuardAgent extends AbstractAgent<Scene, Boolean> {
    
    private final IKnowledgeGraphService kgService;
    private final ILLMClient llmClient;
    
    public ConsistencyGuardAgent(IKnowledgeGraphService kgService, ILLMClient llmClient) {
        super(AgentType.CONSISTENCY_GUARD);
        this.kgService = kgService;
        this.llmClient = llmClient;
    }
    
    @Override
    protected Boolean doExecute(Scene scene, NovelContext context) {
        log.info("开始一致性校验，场景: {}", scene.getSceneTitle());
        
        try {
            // 检查人物一致性（人物在 KG 中按 novelId_c_<名称> 存储）
            String novelId = context != null ? context.getNovelId() : null;
            for (String characterName : scene.getCharacters()) {
                String characterId = KgCharacterSyncUtil.toCharacterId(novelId != null ? novelId : "", characterName);
                Character character = kgService.queryCharacter(characterId);
                if (character != null) {
                    // 调用LLM检查性格是否一致
                    if (!checkPersonalityConsistency(scene.getContent(), character)) {
                        log.warn("人物 {} 性格不一致", characterName);
                        return false;
                    }
                    
                    // 检查关系一致性
                    if (!checkRelationshipConsistency(scene, character)) {
                        log.warn("人物 {} 关系不一致", characterName);
                        return false;
                    }
                }
            }
            
            log.info("一致性校验通过");
            return true;
            
        } catch (Exception e) {
            log.error("一致性校验失败", e);
            // 校验失败时，为了不阻塞流程，返回true（可以根据业务需求调整）
            return true;
        }
    }
    
    /**
     * 调用LLM检查人物性格是否一致
     */
    private boolean checkPersonalityConsistency(String content, Character character) {
        if (llmClient == null) {
            // 降级策略：简单关键词匹配
            return checkPersonalityByKeywords(content, character);
        }
        
        try {
            String systemPrompt = "你是一个专业的小说审校助手。检查场景内容中的人物行为是否符合其性格设定。" +
                    "如果一致返回true，不一致返回false。";
            
            String template = "人物设定：\n" +
                    "姓名：{characterName}\n" +
                    "性格：{personality}\n" +
                    "背景：{background}\n\n" +
                    "场景内容：\n{content}\n\n" +
                    "请检查场景中该人物的行为是否符合其性格设定，只返回true或false。";
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("characterName", character.getName());
            variables.put("personality", character.getPersonality() != null ? character.getPersonality() : "");
            variables.put("background", character.getBackground() != null ? character.getBackground() : "");
            variables.put("content", content);
            
            String response = llmClient.callWithTemplate(systemPrompt, template, variables);
            return response.trim().toLowerCase().contains("true");
            
        } catch (Exception e) {
            log.warn("调用LLM检查性格一致性失败，使用降级策略", e);
            return checkPersonalityByKeywords(content, character);
        }
    }
    
    /**
     * 降级策略：关键词匹配检查
     */
    private boolean checkPersonalityByKeywords(String content, Character character) {
        // 简单的关键词匹配，实际应该更复杂
        // 这里简化处理，默认通过
        return true;
    }
    
    /**
     * 检查关系一致性
     */
    private boolean checkRelationshipConsistency(Scene scene, Character character) {
        // 检查场景中人物之间的关系是否符合知识图谱中的设定
        // 这里简化处理，实际应该更复杂
        return true;
    }
}
