package cn.bugstack.novel.domain.service.plot;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.StoryProgress;

import java.util.List;

/**
 * 剧情追踪服务（Plot Tracker）
 * 维护：伏笔、角色目标、剧情进度，为 EndingAgent 提供输入。
 * 组合 MySQL（规划/章节）与 Neo4j（伏笔）信息。
 */
public interface IPlotTrackerService {

    /**
     * 根据当前上下文构建故事进度快照，供结局判定使用
     *
     * @param context      小说生成上下文（含 plan、currentVolume、currentChapter 等）
     * @param storySummary 当前剧情摘要（可为整体大纲摘要或最近几章梗概拼接）
     * @return 故事进度，若信息不足则部分字段为 null/空
     */
    StoryProgress buildProgress(NovelContext context, String storySummary);

    /**
     * 收集某小说下未解决伏笔：优先从 KG 查询，并与传入的章节伏笔列表合并去重
     *
     * @param novelId           小说ID
     * @param fromChapterOutlines 来自章节梗概的伏笔描述列表（可为 null）
     * @return 未解决伏笔描述列表
     */
    List<String> collectUnresolvedForeshadowing(String novelId, List<String> fromChapterOutlines);
}
