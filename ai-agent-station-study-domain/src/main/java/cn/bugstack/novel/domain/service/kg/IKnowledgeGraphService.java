package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.model.valobj.Character;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱服务接口
 */
public interface IKnowledgeGraphService {

    // ========== 图数据（可视化） ==========

    /**
     * 按小说ID获取知识图谱（人物、伏笔、关系），供前端渲染
     *
     * @param novelId 小说ID
     * @return 节点与边
     */
    KGGraphDTO getGraph(String novelId);

    /**
     * 按小说ID列出人物列表
     */
    List<Map<String, Object>> listCharacters(String novelId);

    /**
     * 按小说ID列出伏笔列表（含 id、content、resolved 等）
     */
    List<Map<String, Object>> listForeshadowing(String novelId);

    /**
     * 删除人物节点（及其关系）
     */
    void deleteCharacter(String characterId);

    /**
     * 删除伏笔节点
     */
    void deleteForeshadowing(String foreshadowingId);

    /**
     * 更新人物节点
     */
    void updateCharacter(Character character);

    /**
     * 更新伏笔（内容、resolved 等）
     */
    void updateForeshadowing(String foreshadowingId, String content, Map<String, Object> properties);

    /**
     * 标记伏笔为已回收
     */
    default void resolveForeshadowing(String foreshadowingId) {
        updateForeshadowing(foreshadowingId, null, java.util.Map.of("resolved", true));
    }

    /**
     * 批量标记伏笔为已回收（如结局时）
     */
    default void resolveForeshadowings(String novelId, java.util.List<String> foreshadowingIds) {
        if (foreshadowingIds == null) return;
        for (String id : foreshadowingIds) {
            resolveForeshadowing(id);
        }
    }

    // ========== 原有接口 ==========
    
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
     * @param properties 建议包含 novelId、chapterId、resolved(boolean) 等
     */
    void createForeshadowing(String foreshadowingId, String content, Map<String, Object> properties);
    
    /**
     * 查询某小说下未解决的伏笔列表（供 Plot Tracker / EndingAgent 使用）
     * @param novelId 小说ID
     * @return 伏笔内容描述列表，无则返回空列表
     */
    List<String> listUnresolvedForeshadowing(String novelId);

    /**
     * 查询某小说下未解决伏笔的 ID 列表（供批量回收使用）
     */
    default List<String> listUnresolvedForeshadowingIds(String novelId) {
        return new java.util.ArrayList<>();
    }

    /**
     * 查询活跃剧情线程（进行中/未解决），供生成与结局判定使用。
     */
    default List<String> listActivePlotThreads(String novelId, int limit) {
        return new java.util.ArrayList<>();
    }

    /**
     * 通用实体写入接口，用于补充 Location / Event / Artifact / Technique 等节点。
     */
    default void upsertEntity(String novelId, String entityType, String entityId, String name, Map<String, Object> properties) {
        // default no-op
    }

    /**
     * 写入剧情线程节点，用于替代“仅伏笔列表”的弱结构化存储。
     */
    default void upsertPlotThread(String novelId, String threadId, String title, String status, Map<String, Object> properties) {
        // default no-op
    }

    /**
     * 创建跨类型实体关系，例如 Character-[:APPEARS_AT]->Location。
     */
    default void createEntityRelationship(String fromType, String fromId, String toType, String toId, String relationType, Map<String, Object> properties) {
        // default no-op
    }

    /**
     * 读取与当前章节相关的结构化知识上下文。
     */
    default StoryKnowledgeSnapshot buildStoryKnowledge(String novelId, List<String> characterNames, String location, List<String> eventNames, int limit) {
        return StoryKnowledgeSnapshot.builder().build();
    }
    
    /**
     * 校验规则
     *
     * @param rule 规则Cypher查询
     * @return 是否违反规则
     */
    boolean validateRule(String rule);

    /**
     * 检查图数据库是否已配置且可连接
     */
    default boolean isConnected() {
        return false;
    }
    
}
