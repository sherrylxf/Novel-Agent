package cn.bugstack.novel.domain.agent.service.data.impl;

import cn.bugstack.novel.domain.agent.adapter.repository.INovelRepository;
import cn.bugstack.novel.domain.agent.service.data.ILoadDataStrategy;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.valobj.NovelSeedVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 小说数据加载策略
 * 并发加载小说相关数据
 * 参考课程第3-6节：数据加载模型设计
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Service("novelLoadDataStrategy")
public class NovelLoadDataStrategy implements ILoadDataStrategy {
    
    @Resource
    private INovelRepository repository;
    
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    
    @Override
    public void loadData(String novelId, NovelContext context) {
        log.info("开始加载小说数据，novelId: {}", novelId);
        
        // 如果novelId为空，说明是新生成，不需要加载数据
        if (novelId == null || novelId.isEmpty()) {
            log.info("novelId为空，跳过数据加载");
            return;
        }
        
        // 并发查询多个数据表
        CompletableFuture<NovelSeedVO> seedFuture = CompletableFuture.supplyAsync(() -> {
            log.info("查询小说Seed数据，novelId: {}", novelId);
            return repository.queryNovelSeedByNovelId(novelId);
        }, threadPoolExecutor);
        
        CompletableFuture<List<Object>> planFuture = CompletableFuture.supplyAsync(() -> {
            log.info("查询小说规划数据，novelId: {}", novelId);
            // 这里可以查询规划、卷、章节等数据
            return List.of();
        }, threadPoolExecutor);
        
        // 等待所有查询完成
        CompletableFuture.allOf(seedFuture, planFuture).thenRun(() -> {
            // 设置结果到上下文
            NovelSeedVO seed = seedFuture.join();
            if (seed != null) {
                context.setAttribute("loadedSeed", seed);
            }
            context.setAttribute("plan", planFuture.join());
            log.info("小说数据加载完成，novelId: {}", novelId);
        }).join();
    }
    
}
