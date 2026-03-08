package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.agent.service.data.ILoadDataStrategy;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 根执行节点
 * 负责数据加载
 * 参考课程第3-10、11节：执行链路设计
 */
@Slf4j
@Component
public class RootExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    private Map<String, ILoadDataStrategy> loadDataStrategyMap;
    
    @Resource
    private SeedExecuteNode seedExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("根节点：开始加载数据");
        
        // 加载数据（如果novelId存在）
        String novelId = context.getNovelId();
        if (novelId != null && !novelId.isEmpty()) {
            ILoadDataStrategy loadDataStrategy = loadDataStrategyMap.get("novelLoadDataStrategy");
            if (loadDataStrategy != null) {
                loadDataStrategy.loadData(novelId, context);
            }
        }
        
        context.setCurrentStage(GenerationStage.SEED.name()); // 使用枚举名称
        return seedExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return seedExecuteNode;
    }
    
}
