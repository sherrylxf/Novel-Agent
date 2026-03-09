package cn.bugstack.novel.domain.service.rag;

import cn.bugstack.novel.domain.model.entity.StoryContextBundle;
import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.model.entity.StoryRetrievalQuery;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一汇总 RAG 与 KG 信息，生成适合写作 Agent 使用的上下文。
 */
@Service
public class StoryContextBuilderService {

    private final IRAGService ragService;

    public StoryContextBuilderService(IRAGService ragService) {
        this.ragService = ragService;
    }

    public StoryContextBundle buildSceneContext(StoryRetrievalQuery retrievalQuery,
                                                StoryKnowledgeSnapshot knowledgeSnapshot,
                                                List<String> unresolvedForeshadowing) {
        List<IRAGService.SearchResult> memories = retrievalQuery != null
                ? ragService.searchStoryMemories(retrievalQuery)
                : new ArrayList<>();

        Map<String, List<IRAGService.SearchResult>> grouped = memories.stream()
                .collect(Collectors.groupingBy(this::memoryTypeOf, LinkedHashMap::new, Collectors.toList()));

        String historyBackground = buildHistoryBackground(grouped, knowledgeSnapshot);
        String characterMemory = buildCharacterMemory(grouped, knowledgeSnapshot);
        String activeThreads = buildActiveThreads(grouped, knowledgeSnapshot, unresolvedForeshadowing);

        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("queryText", retrievalQuery != null ? retrievalQuery.getQueryText() : "");
        explain.put("characters", retrievalQuery != null ? retrievalQuery.getCharacters() : List.of());
        explain.put("locations", retrievalQuery != null ? retrievalQuery.getLocations() : List.of());
        explain.put("topics", retrievalQuery != null ? retrievalQuery.getTopics() : List.of());
        explain.put("plotThreads", retrievalQuery != null ? retrievalQuery.getPlotThreads() : List.of());
        explain.put("resultCount", memories.size());
        explain.put("memoryTypeBreakdown", grouped.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size(),
                        (left, right) -> left, LinkedHashMap::new)));

        return StoryContextBundle.builder()
                .historyBackground(historyBackground)
                .characterMemory(characterMemory)
                .activeThreads(activeThreads)
                .promptContext(joinSections(historyBackground, characterMemory, activeThreads))
                .citedMemories(memories)
                .retrievalExplain(explain)
                .build();
    }

    private String buildHistoryBackground(Map<String, List<IRAGService.SearchResult>> grouped,
                                          StoryKnowledgeSnapshot knowledgeSnapshot) {
        List<String> lines = new ArrayList<>();
        lines.addAll(toRagLines(grouped.get("chapter_summary"), 2));
        lines.addAll(toRagLines(grouped.get("scene_summary"), 2));
        lines.addAll(trimFactLines(knowledgeSnapshot != null ? knowledgeSnapshot.getEventFacts() : null, 3));
        return formatSection("历史背景", lines);
    }

    private String buildCharacterMemory(Map<String, List<IRAGService.SearchResult>> grouped,
                                        StoryKnowledgeSnapshot knowledgeSnapshot) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        lines.addAll(trimFactLines(knowledgeSnapshot != null ? knowledgeSnapshot.getCharacterFacts() : null, 4));
        lines.addAll(trimFactLines(knowledgeSnapshot != null ? knowledgeSnapshot.getLocationFacts() : null, 2));
        lines.addAll(toRagLines(grouped.get("character_memory"), 2));
        return formatSection("人物与地点约束", new ArrayList<>(lines));
    }

    private String buildActiveThreads(Map<String, List<IRAGService.SearchResult>> grouped,
                                      StoryKnowledgeSnapshot knowledgeSnapshot,
                                      List<String> unresolvedForeshadowing) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        lines.addAll(trimFactLines(knowledgeSnapshot != null ? knowledgeSnapshot.getActivePlotThreads() : null, 4));
        lines.addAll(trimFactLines(knowledgeSnapshot != null ? knowledgeSnapshot.getUnresolvedClues() : null, 3));
        lines.addAll(trimFactLines(unresolvedForeshadowing, 3));
        lines.addAll(toRagLines(grouped.get("plot_thread_summary"), 2));
        return formatSection("相关剧情线程", new ArrayList<>(lines));
    }

    private List<String> toRagLines(List<IRAGService.SearchResult> results, int limit) {
        if (results == null || results.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        int index = 0;
        for (IRAGService.SearchResult result : results) {
            if (result == null || !KgStorySyncUtil.hasMeaningfulText(result.getContent())) {
                continue;
            }
            String memoryType = memoryTypeOf(result);
            lines.add("[" + memoryType + "] " + StoryMemoryDocumentUtil.excerpt(result.getContent(), 180));
            index++;
            if (index >= limit) {
                break;
            }
        }
        return lines;
    }

    private List<String> trimFactLines(List<String> facts, int limit) {
        if (facts == null || facts.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String fact : facts) {
            if (!KgStorySyncUtil.hasMeaningfulText(fact)) {
                continue;
            }
            lines.add(StoryMemoryDocumentUtil.excerpt(fact.trim(), 180));
            if (lines.size() >= limit) {
                break;
            }
        }
        return lines;
    }

    private String formatSection(String title, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return title + "：\n" + lines.stream()
                .filter(KgStorySyncUtil::hasMeaningfulText)
                .map(line -> "- " + line.trim())
                .collect(Collectors.joining("\n"));
    }

    private String joinSections(String... sections) {
        StringBuilder sb = new StringBuilder();
        if (sections == null) {
            return "";
        }
        for (String section : sections) {
            if (!KgStorySyncUtil.hasMeaningfulText(section)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(section.trim());
        }
        return sb.toString();
    }

    private String memoryTypeOf(IRAGService.SearchResult result) {
        if (result == null || result.getMetadata() == null) {
            return "unknown";
        }
        Object memoryType = result.getMetadata().get("memoryType");
        return memoryType != null ? memoryType.toString() : "unknown";
    }
}
