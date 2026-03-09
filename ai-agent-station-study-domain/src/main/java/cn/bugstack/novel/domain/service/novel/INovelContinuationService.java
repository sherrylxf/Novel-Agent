package cn.bugstack.novel.domain.service.novel;

import cn.bugstack.novel.domain.model.entity.NovelContext;

/**
 * 小说续写恢复服务
 */
public interface INovelContinuationService {

    /**
     * 基于数据库已保存数据恢复续写上下文
     */
    NovelContext buildResumeContext(String novelId);
}
