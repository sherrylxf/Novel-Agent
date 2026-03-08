package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 从知识图谱中提取的结构化记忆快照。
 * 用于在生成章节/场景时补充人物、世界观与剧情线程上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryKnowledgeSnapshot {

    @Builder.Default
    private List<String> worldFacts = new ArrayList<>();

    @Builder.Default
    private List<String> characterFacts = new ArrayList<>();

    @Builder.Default
    private List<String> activePlotThreads = new ArrayList<>();
}
