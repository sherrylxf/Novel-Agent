package cn.bugstack.novel.domain.agent.service.armory;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.service.data.ILoadDataStrategy;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Map;

/**
 * Novel Agent装配服务
 * 参考课程第3-7、8、9节：动态实例化机制
 * 负责动态注册和管理Agent实例
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Service
public class NovelAgentArmoryService extends AbstractNovelArmorySupport {
    
    @Resource
    private ApplicationContext applicationContext;
    
    @Resource
    private Map<String, ILoadDataStrategy> loadDataStrategyMap;
    
    @PostConstruct
    public void init() {
        setApplicationContext(applicationContext);
        log.info("Novel Agent装配服务初始化完成");
    }
    
    /**
     * 装配Novel Agent
     * 根据novelId动态加载配置并注册Agent
     *
     * @param novelId 小说ID
     * @param context 上下文
     */
    public void armoryNovelAgent(String novelId, NovelContext context) {
        log.info("开始装配Novel Agent，novelId: {}", novelId);
        
        // 1. 加载数据（使用策略模式）
        ILoadDataStrategy loadDataStrategy = loadDataStrategyMap.get("novelLoadDataStrategy");
        if (loadDataStrategy != null) {
            loadDataStrategy.loadData(novelId, context);
        }
        
        // 2. 动态注册Agent（这里可以根据配置动态创建Agent实例）
        // 例如：根据novelId查询配置，动态创建特定配置的Agent
        
        log.info("Novel Agent装配完成，novelId: {}", novelId);
    }
    
    @Override
    public void execute(NovelContext context) {
        String novelId = context.getNovelId();
        if (novelId != null) {
            armoryNovelAgent(novelId, context);
        }
    }
    
}
