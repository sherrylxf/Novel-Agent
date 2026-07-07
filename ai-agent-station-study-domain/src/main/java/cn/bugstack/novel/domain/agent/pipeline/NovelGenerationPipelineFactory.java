package cn.bugstack.novel.domain.agent.pipeline;

import cn.bugstack.novel.domain.agent.service.execute.chain.RootExecuteNode;
import cn.bugstack.novel.types.enums.GenerationStage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 小说生成 Pipeline 工厂：提供<strong>责任链入口</strong>与<strong>阶段顺序声明</strong>。
 * <p>
 * 与经典「setNextHandler 手工串链」不同，本项目中各阶段由 Spring 注入为独立 Bean，
 * 链的组装通过各节点 {@code doExecute} 返回下一跳完成；本工厂用于统一入口与顺序文档化，
 * 便于与状态机、面试表述对齐。
 */
@Component
public class NovelGenerationPipelineFactory {

    @Resource
    private RootExecuteNode rootExecuteNode;

    /**
     * 从根节点进入流水线（数据加载后由根节点转入 SEED 等后续阶段）。
     */
    public IGenerationPipelineHandler createPipelineEntry() {
        return rootExecuteNode;
    }

    /**
     * 主链业务阶段顺序（不含动态分支）。
     */
    public List<GenerationStage> orderedBusinessStages() {
        return NovelPipelineTopology.PRIMARY_LINEAR_STAGES;
    }
}
