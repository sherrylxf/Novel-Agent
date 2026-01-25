package cn.bugstack.novel.domain.agent;

import cn.bugstack.novel.domain.model.entity.NovelContext;

/**
 * Agent接口
 * 所有Agent必须实现此接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public interface IAgent<T, R> {
    
    /**
     * 执行Agent任务
     *
     * @param input 输入参数
     * @param context 小说上下文
     * @return 执行结果
     */
    R execute(T input, NovelContext context);
    
    /**
     * 获取Agent名称
     *
     * @return Agent名称
     */
    String getName();
    
    /**
     * 获取Agent类型
     *
     * @return Agent类型
     */
    String getType();
    
}
