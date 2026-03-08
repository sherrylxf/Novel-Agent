package cn.bugstack.novel.domain.service.persistence;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.VolumePlan;

/**
 * 生成过程数据持久化服务
 */
public interface INovelGenerationStoreService {

    void persistSeed(String novelId, NovelSeed seed);

    void persistPlan(NovelPlan plan);

    void persistVolumePlan(String novelId, VolumePlan volumePlan);

    /**
     * 持久化章节与场景，并导出 txt
     * @return 最终 chapterId
     */
    String persistChapterAndScene(
            String novelId,
            Integer volumeNumber,
            ChapterOutline outline,
            Scene scene
    );
}

