package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 故事进度快照
 * 用于结局判定时提供剧情概要、伏笔与进度信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryProgress {

    /**
     * 当前章节之前的剧情概要（可为多章合并摘要）
     */
    private String storySummary;

    /**
     * 当前已生成到的章节序号（从1开始）
     */
    private Integer currentChapterNumber;

    /**
     * 规划中的总章节数（例如 totalVolumes * chaptersPerVolume）
     */
    private Integer plannedTotalChapters;

    /**
     * 尚未回收/解决的伏笔描述列表
     */
    private List<String> unresolvedForeshadowings;

    /**
     * 仍在推进中的剧情线程列表。
     */
    private List<String> activePlotThreads;
}

