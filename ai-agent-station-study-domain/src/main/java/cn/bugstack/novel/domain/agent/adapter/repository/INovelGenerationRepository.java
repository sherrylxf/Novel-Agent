package cn.bugstack.novel.domain.agent.adapter.repository;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;

/**
 * 生成过程数据落库仓储（Seed/Plan/Volume/Chapter/Scene）
 * 领域层定义接口，由基础设施层实现
 */
public interface INovelGenerationRepository {

    void saveOrUpdateSeed(String novelId, NovelSeed seed);

    void saveOrUpdatePlan(NovelPlan plan);

    void saveOrUpdateVolumePlan(String novelId, VolumePlan volumePlan);

    /**
     * 保存或更新章节（包含梗概与正文）
     * @return 最终落库的 chapterId（可能复用历史记录）
     */
    String saveOrUpdateChapter(String novelId, Integer volumeNumber, ChapterOutline outline, Scene scene);

    void saveOrUpdateScene(String chapterId, Scene scene);
}

