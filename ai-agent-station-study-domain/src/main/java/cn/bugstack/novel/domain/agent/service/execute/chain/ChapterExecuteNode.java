package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KgCharacterSyncUtil;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 章节执行节点
 * 生成章节梗概
 */
@Slf4j
@Component
public class ChapterExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private SceneExecuteNode sceneExecuteNode;
    
    @Autowired(required = false)
    private IKnowledgeGraphService knowledgeGraphService;

    @Resource
    private IRAGService ragService;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("章节节点：开始生成章节梗概");
        
        // 从上下文获取VolumePlan与当前章节序号（多章循环时由 Validation 回填）
        VolumePlan volumePlan = context.getAttribute("currentVolume");
        Integer chapterInVolume = context.getAttribute("currentChapterInVolume");
        if (chapterInVolume == null) chapterInVolume = 1;
        
        // 执行章节梗概生成
        IAgent<Object[], ChapterOutline> chapterAgent = orchestrator.getAgent("ChapterOutlineAgent");
        ChapterOutline outline = chapterAgent.execute(new Object[]{volumePlan, chapterInVolume}, context);
        
        // 保存到上下文（供 Plot Tracker / 多章循环使用）
        context.setAttribute("currentChapter", outline);
        context.setAttribute("currentChapterInVolume", outline != null ? outline.getChapterNumber() : chapterInVolume);
        mergeForeshadowingsToContext(context, outline);

        // 章节梗概优先进入 RAG，形成章节级记忆，而不是只存正文。
        if (outline != null) {
            try {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("novelId", context.getNovelId());
                metadata.put("volumeNumber", volumePlan != null ? volumePlan.getVolumeNumber() : null);
                metadata.put("chapterId", outline.getChapterId());
                metadata.put("chapterNumber", outline.getChapterNumber());
                metadata.put("chapterTitle", outline.getChapterTitle());
                metadata.put("memoryType", "chapter_summary");
                metadata.put("scope", "chapter");
                metadata.put("characters", StoryMemoryDocumentUtil.join(outline.getKeyCharacters(), ","));
                metadata.put("events", StoryMemoryDocumentUtil.join(outline.getKeyEvents(), ","));
                metadata.put("foreshadowing", StoryMemoryDocumentUtil.join(outline.getForeshadowing(), ","));
                ragService.addDocument(StoryMemoryDocumentUtil.buildChapterSummaryDocument(outline), "zh", metadata);
            } catch (Exception e) {
                log.warn("章节梗概写入向量库失败，novelId={}, err={}", context.getNovelId(), e.getMessage());
            }
        }
        
        // 将本章伏笔写入知识图谱，供 Plot Tracker / EndingAgent 使用（可选，失败不阻塞）
        if (knowledgeGraphService != null && outline != null && outline.getForeshadowing() != null && !outline.getForeshadowing().isEmpty()) {
            String novelId = context.getNovelId();
            String chapterId = outline.getChapterId() != null ? outline.getChapterId() : UUID.randomUUID().toString();
            for (int i = 0; i < outline.getForeshadowing().size(); i++) {
                try {
                    String content = outline.getForeshadowing().get(i);
                    if (content == null || content.isEmpty()) continue;
                    Map<String, Object> props = new HashMap<>();
                    props.put("novelId", novelId);
                    props.put("chapterId", chapterId);
                    props.put("resolved", false);
                    String foreshadowingId = "f-" + chapterId + "-" + i;
                    knowledgeGraphService.createForeshadowing(foreshadowingId, content, props);
                } catch (Exception e) {
                    log.warn("伏笔写入知识图谱失败，跳过本条，err: {}", e.getMessage());
                }
            }
        }
        // 将本章关键人物同步到知识图谱，并建立人物关系边（同一章出现则 RELATED_TO），丰富图谱
        if (knowledgeGraphService != null && outline != null && outline.getKeyCharacters() != null && !outline.getKeyCharacters().isEmpty()) {
            String novelId = context.getNovelId() != null ? context.getNovelId() : "";
            List<Character> toSync = KgCharacterSyncUtil.buildCharactersForSync(novelId, outline.getKeyCharacters(), "配角");
            for (Character c : toSync) {
                try {
                    knowledgeGraphService.createCharacter(c);
                } catch (Exception e) {
                    log.warn("人物写入知识图谱失败，跳过，name={}, err: {}", c.getName(), e.getMessage());
                }
            }
            if (!toSync.isEmpty()) {
                log.info("KG存储: 本章关键人物已同步到知识图谱, novelId={}, count={}", novelId, toSync.size());
            }
            // 同一章出现的多个人物之间建立关系边（Neo4j 关系类型用英文 RELATED_TO）
            List<String> names = outline.getKeyCharacters();
            if (names.size() >= 2) {
                for (int i = 0; i < names.size(); i++) {
                    for (int j = i + 1; j < names.size(); j++) {
                        String fromId = KgCharacterSyncUtil.toCharacterId(novelId, names.get(i));
                        String toId = KgCharacterSyncUtil.toCharacterId(novelId, names.get(j));
                        try {
                            Map<String, Object> relProps = new HashMap<>();
                            relProps.put("chapterId", outline.getChapterId());
                            relProps.put("chapterNumber", outline.getChapterNumber());
                            knowledgeGraphService.createRelationship(fromId, toId, "RELATED_TO", relProps);
                        } catch (Exception e) {
                            log.warn("人物关系写入知识图谱失败，跳过，{}->{}, err: {}", fromId, toId, e.getMessage());
                        }
                    }
                }
            }

            // 同步本章关键事件与剧情线程，为 Scene 生成提供结构化上下文。
            if (outline.getKeyEvents() != null) {
                for (String eventName : outline.getKeyEvents()) {
                    if (!KgStorySyncUtil.hasMeaningfulText(eventName)) {
                        continue;
                    }
                    try {
                        String eventId = KgStorySyncUtil.toEventId(novelId, eventName);
                        Map<String, Object> eventProps = new HashMap<>();
                        eventProps.put("chapterId", outline.getChapterId());
                        eventProps.put("chapterNumber", outline.getChapterNumber());
                        eventProps.put("summary", outline.getOutline());
                        knowledgeGraphService.upsertEntity(novelId, "Event", eventId, eventName, eventProps);
                        for (String characterName : outline.getKeyCharacters()) {
                            String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                            knowledgeGraphService.createEntityRelationship("Character", characterId, "Event", eventId, "INVOLVED_IN", eventProps);
                        }
                    } catch (Exception e) {
                        log.warn("章节事件写入知识图谱失败，event={}, err={}", eventName, e.getMessage());
                    }
                }
            }

            if (outline.getForeshadowing() != null) {
                String chapterId = outline.getChapterId() != null ? outline.getChapterId() : java.util.UUID.randomUUID().toString();
                for (int i = 0; i < outline.getForeshadowing().size(); i++) {
                    String threadTitle = outline.getForeshadowing().get(i);
                    if (!KgStorySyncUtil.hasMeaningfulText(threadTitle)) continue;
                    try {
                        String threadId = KgStorySyncUtil.toPlotThreadId(novelId, threadTitle);
                        Map<String, Object> threadProps = new HashMap<>();
                        threadProps.put("chapterId", chapterId);
                        threadProps.put("chapterNumber", outline.getChapterNumber());
                        threadProps.put("summary", outline.getOutline());
                        knowledgeGraphService.upsertPlotThread(novelId, threadId, threadTitle, "ACTIVE", threadProps);
                        for (String characterName : outline.getKeyCharacters()) {
                            String characterId = KgCharacterSyncUtil.toCharacterId(novelId, characterName);
                            knowledgeGraphService.createEntityRelationship("Character", characterId, "PlotThread", threadId, "INVOLVED_IN", threadProps);
                        }
                        // 关联 PlotThread -> Foreshadowing，使伏笔在图谱中形成连线
                        String foreshadowingId = "f-" + chapterId + "-" + i;
                        knowledgeGraphService.createEntityRelationship("PlotThread", threadId, "Foreshadowing", foreshadowingId, "REFERS_TO", threadProps);
                    } catch (Exception e) {
                        log.warn("剧情线程写入知识图谱失败，thread={}, err={}", threadTitle, e.getMessage());
                    }
                }
            }
        }
        // 同步到 volumePlan.chapterOutlines，便于后续保存/展示
        if (volumePlan != null) {
            if (volumePlan.getChapterOutlines() == null) {
                volumePlan.setChapterOutlines(new ArrayList<>());
            }
            volumePlan.getChapterOutlines().removeIf(c -> c != null && c.getChapterNumber() != null
                    && outline != null && c.getChapterNumber().equals(outline.getChapterNumber()));
            volumePlan.getChapterOutlines().add(outline);
        }
        context.setCurrentStage(GenerationStage.SCENE_GENERATION.name());
        
        return sceneExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return sceneExecuteNode;
    }

    @SuppressWarnings("unchecked")
    private void mergeForeshadowingsToContext(NovelContext context, ChapterOutline outline) {
        if (context == null || outline == null || outline.getForeshadowing() == null || outline.getForeshadowing().isEmpty()) {
            return;
        }
        List<String> current = context.getAttribute("unresolvedForeshadowingsFromChapters");
        if (current == null) {
            current = new ArrayList<>();
        }
        for (String item : outline.getForeshadowing()) {
            if (item != null && !item.trim().isEmpty() && !current.contains(item.trim())) {
                current.add(item.trim());
            }
        }
        context.setAttribute("unresolvedForeshadowingsFromChapters", current);
    }
    
}
