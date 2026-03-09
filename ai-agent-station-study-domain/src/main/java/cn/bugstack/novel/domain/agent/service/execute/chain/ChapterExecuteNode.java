package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.service.kg.KgCharacterSyncUtil;
import cn.bugstack.novel.domain.service.kg.KgSyncFacade;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    @Resource
    private KgSyncFacade kgSyncFacade;

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
                String createdAt = LocalDateTime.now().toString();
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
                metadata.put("plotThreads", StoryMemoryDocumentUtil.join(outline.getForeshadowing(), ","));
                metadata.put("characterIds", KgStorySyncUtil.toCharacterIds(context.getNovelId(), outline.getKeyCharacters()));
                metadata.put("eventIds", buildChapterEventIds(context.getNovelId(), outline));
                metadata.put("plotThreadIds", KgStorySyncUtil.toPlotThreadIds(context.getNovelId(), outline.getForeshadowing()));
                metadata.put("factionIds", new ArrayList<String>());
                metadata.put("createdAt", createdAt);
                metadata.put("importance", 0.8D);
                ragService.addDocument(StoryMemoryDocumentUtil.buildChapterSummaryDocument(outline), "zh", metadata);

                for (String threadTitle : KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing())) {
                    Map<String, Object> threadMetadata = new HashMap<>(metadata);
                    threadMetadata.put("memoryType", "plot_thread_summary");
                    threadMetadata.put("scope", "plot_thread");
                    threadMetadata.put("plotThreads", threadTitle);
                    threadMetadata.put("importance", 0.75D);
                    ragService.addDocument(
                            StoryMemoryDocumentUtil.buildPlotThreadSummaryDocument(
                                    threadTitle,
                                    outline,
                                    null,
                                    "章节梗概中标记的剧情线索",
                                    outline.getKeyEvents()),
                            "zh",
                            threadMetadata);
                }
            } catch (Exception e) {
                log.warn("章节梗概写入向量库失败，novelId={}, err={}", context.getNovelId(), e.getMessage());
            }
        }
        
        if (outline != null) {
            kgSyncFacade.syncChapterOutline(context.getNovelId(), outline);
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

    private List<String> buildChapterEventIds(String novelId, ChapterOutline outline) {
        List<String> ids = new ArrayList<>();
        if (outline == null || outline.getKeyEvents() == null) {
            return ids;
        }
        for (String eventName : KgStorySyncUtil.distinctNonBlank(outline.getKeyEvents())) {
            ids.add(KgStorySyncUtil.toChapterEventId(novelId, outline.getChapterId(), eventName));
        }
        return ids;
    }
    
}
