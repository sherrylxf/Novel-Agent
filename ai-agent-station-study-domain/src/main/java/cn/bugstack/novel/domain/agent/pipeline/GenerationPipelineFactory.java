package cn.bugstack.novel.domain.agent.pipeline;

import cn.bugstack.novel.domain.agent.service.execute.chain.ChapterExecuteNode;
import cn.bugstack.novel.domain.agent.service.execute.chain.PlanExecuteNode;
import cn.bugstack.novel.domain.agent.service.execute.chain.RootExecuteNode;
import cn.bugstack.novel.domain.agent.service.execute.chain.SceneExecuteNode;
import cn.bugstack.novel.domain.agent.service.execute.chain.SeedExecuteNode;
import cn.bugstack.novel.domain.agent.service.execute.chain.ValidationExecuteNode;
import cn.bugstack.novel.domain.agent.service.execute.chain.VolumeExecuteNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标准小说生成责任链装配入口
 * <p>
 * 生产环境后继关系由各 {@code ExecuteNode} 的 Spring 注入字段表达（等价于
 * {@link IGenerationPipelineHandler#setNextHandler} 的静态拓扑）；本类集中暴露「链头」与顺序清单，便于扩展与单测。
 */
@Component
public class GenerationPipelineFactory {

    @Resource
    private RootExecuteNode rootExecuteNode;
    @Resource
    private SeedExecuteNode seedExecuteNode;
    @Resource
    private PlanExecuteNode planExecuteNode;
    @Resource
    private VolumeExecuteNode volumeExecuteNode;
    @Resource
    private ChapterExecuteNode chapterExecuteNode;
    @Resource
    private SceneExecuteNode sceneExecuteNode;
    @Resource
    private ValidationExecuteNode validationExecuteNode;

    /**
     * 责任链入口：根节点负责加载数据并将上下文切到 SEED 阶段。
     */
    public IGenerationPipelineHandler standardPipelineHead() {
        return rootExecuteNode;
    }

    /**
     * 典型拓扑顺序（VALIDATION 之后可能回到卷规划或章节梗概，由运行时上下文决定，不在此列表展开分支）。
     */
    public List<IGenerationPipelineHandler> standardPipelineOrderedHandlers() {
        return List.of(
                rootExecuteNode,
                seedExecuteNode,
                planExecuteNode,
                volumeExecuteNode,
                chapterExecuteNode,
                sceneExecuteNode,
                validationExecuteNode
        );
    }
}
