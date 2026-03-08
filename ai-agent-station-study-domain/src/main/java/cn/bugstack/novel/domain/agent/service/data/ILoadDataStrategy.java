package cn.bugstack.novel.domain.agent.service.data;

import cn.bugstack.novel.domain.model.entity.NovelContext;

/**
 * 数据加载策略接口
 * 参考课程第3-6节：数据加载模型设计
 */
public interface ILoadDataStrategy {
    
    /**
     * 加载数据
     *
     * @param novelId 小说ID
     * @param context 上下文
     */
    void loadData(String novelId, NovelContext context);
    
}
