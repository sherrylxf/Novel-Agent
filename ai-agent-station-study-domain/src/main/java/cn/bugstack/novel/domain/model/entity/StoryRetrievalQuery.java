package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小说生成阶段使用的结构化检索请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryRetrievalQuery {

    private String novelId;

    private String queryText;

    @Builder.Default
    private List<String> characters = new ArrayList<>();

    @Builder.Default
    private List<String> locations = new ArrayList<>();

    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @Builder.Default
    private List<String> plotThreads = new ArrayList<>();

    @Builder.Default
    private List<String> memoryTypes = new ArrayList<>();

    private Integer chapterFrom;

    private Integer chapterTo;

    @Builder.Default
    private Integer topK = 3;

    @Builder.Default
    private boolean explain = false;

    @Builder.Default
    private Map<String, Object> extraFilters = new LinkedHashMap<>();
}
