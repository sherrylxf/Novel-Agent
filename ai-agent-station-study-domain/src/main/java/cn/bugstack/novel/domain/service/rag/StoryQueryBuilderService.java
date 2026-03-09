package cn.bugstack.novel.domain.service.rag;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.model.entity.StoryRetrievalQuery;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 把当前剧情输入转为结构化检索请求。
 */
@Service
public class StoryQueryBuilderService {

    private static final List<String> DEFAULT_MEMORY_TYPES = List.of(
            "chapter_summary",
            "scene_summary",
            "character_memory",
            "plot_thread_summary",
            "scene_fulltext");

    public StoryRetrievalQuery buildSceneQuery(ChapterOutline outline,
                                               NovelContext context,
                                               StoryKnowledgeSnapshot knowledgeSnapshot) {
        LinkedHashSet<String> characters = new LinkedHashSet<>(KgStorySyncUtil.distinctNonBlank(
                outline != null ? outline.getKeyCharacters() : null));
        LinkedHashSet<String> locations = new LinkedHashSet<>(extractLocations(outline, context));
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        LinkedHashSet<String> plotThreads = new LinkedHashSet<>();

        if (outline != null) {
            topics.addAll(KgStorySyncUtil.distinctNonBlank(outline.getKeyEvents()));
            topics.addAll(KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing()));
            plotThreads.addAll(KgStorySyncUtil.distinctNonBlank(outline.getForeshadowing()));
        }
        if (knowledgeSnapshot != null && knowledgeSnapshot.getActivePlotThreads() != null) {
            plotThreads.addAll(KgStorySyncUtil.distinctNonBlank(knowledgeSnapshot.getActivePlotThreads()));
        }

        Integer currentChapter = outline != null ? outline.getChapterNumber() : null;
        Integer chapterFrom = null;
        if (currentChapter != null && currentChapter > 1) {
            chapterFrom = Math.max(1, currentChapter - 12);
        }

        return StoryRetrievalQuery.builder()
                .novelId(context != null ? context.getNovelId() : null)
                .queryText(StoryMemoryDocumentUtil.buildSearchQuery(outline))
                .characters(new ArrayList<>(characters))
                .locations(new ArrayList<>(locations))
                .topics(new ArrayList<>(topics))
                .plotThreads(new ArrayList<>(plotThreads))
                .memoryTypes(new ArrayList<>(DEFAULT_MEMORY_TYPES))
                .chapterFrom(chapterFrom)
                .chapterTo(currentChapter)
                .topK(3)
                .build();
    }

    private List<String> extractLocations(ChapterOutline outline, NovelContext context) {
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        if (outline != null && outline.getScenes() != null) {
            for (Scene scene : outline.getScenes()) {
                if (scene != null && KgStorySyncUtil.hasMeaningfulText(scene.getLocation())) {
                    locations.add(scene.getLocation().trim());
                }
            }
        }
        Scene currentScene = context != null ? context.getAttribute("currentScene") : null;
        if (currentScene != null && KgStorySyncUtil.hasMeaningfulText(currentScene.getLocation())) {
            locations.add(currentScene.getLocation().trim());
        }
        return new ArrayList<>(locations);
    }
}
