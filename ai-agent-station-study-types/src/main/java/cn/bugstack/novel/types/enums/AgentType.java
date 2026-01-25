package cn.bugstack.novel.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent类型枚举
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Getter
@AllArgsConstructor
public enum AgentType {
    
    // 世界与知识层
    WORLD_KNOWLEDGE_GRAPH("WorldKnowledgeGraphAgent", "世界知识图谱Agent"),
    ENTITY_BUILDER("EntityAndForeshadowingBuilderAgent", "实体和伏笔构建Agent"),
    
    // 规划层
    NOVEL_SEED("NovelSeedAgent", "小说种子Agent"),
    NOVEL_PLANNER("NovelPlannerAgent", "小说规划Agent"),
    VOLUME_PLANNER("VolumePlannerAgent", "卷/册规划Agent"),
    CHAPTER_OUTLINE("ChapterOutlineAgent", "章节梗概Agent"),
    
    // 生成执行层
    SCENE_GENERATION("SceneGenerationAgent", "场景生成Agent"),
    COMBAT_DETAIL("CombatDetailAgent", "战斗细节Agent"),
    EMOTION_CP("EmotionAndCPInteractionAgent", "情感CP互动Agent"),
    
    // 约束与审校层
    CONSISTENCY_GUARD("ConsistencyGuardAgent", "一致性守护Agent"),
    KG_RULE_VALIDATOR("KGRuleValidatorAgent", "知识图谱规则校验Agent"),
    
    // 记忆与检索层
    MULTILINGUAL_RAG("MultilingualRAGAgent", "多语言RAG Agent"),
    MEMORY_MANAGER("MemoryManagerAgent", "记忆管理Agent"),
    
    // 人机协作层
    HUMAN_IN_LOOP("HumanInTheLoopAgent", "人机协作Agent"),
    
    ;
    
    private final String code;
    private final String description;
    
}
