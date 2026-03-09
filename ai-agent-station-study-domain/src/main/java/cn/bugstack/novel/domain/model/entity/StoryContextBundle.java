package cn.bugstack.novel.domain.model.entity;

import cn.bugstack.novel.domain.service.rag.IRAGService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供给 Writer Agent 的压缩上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryContextBundle {

    private String historyBackground;

    private String characterMemory;

    private String activeThreads;

    private String promptContext;

    @Builder.Default
    private List<IRAGService.SearchResult> citedMemories = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> retrievalExplain = new LinkedHashMap<>();
}
