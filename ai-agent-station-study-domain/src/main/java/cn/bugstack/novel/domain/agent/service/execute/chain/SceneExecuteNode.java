package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * 场景执行节点
 * 生成场景正文
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class SceneExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private ValidationExecuteNode validationExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("场景节点：开始生成场景正文");
        
        // 从上下文获取ChapterOutline
        ChapterOutline outline = context.getAttribute("currentChapter");
        
        // 执行场景生成
        IAgent<ChapterOutline, Scene> sceneAgent = orchestrator.getAgent("SceneGenerationAgent");
        Scene scene = sceneAgent.execute(outline, context);
        
        // 保存到上下文
        context.setAttribute("currentScene", scene);
        context.setCurrentStage(GenerationStage.VALIDATION.getName());
        
        return validationExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return validationExecuteNode;
    }
    
}
