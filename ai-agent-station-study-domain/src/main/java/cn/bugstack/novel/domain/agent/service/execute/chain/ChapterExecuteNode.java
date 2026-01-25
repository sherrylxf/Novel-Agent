package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * 章节执行节点
 * 生成章节梗概
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class ChapterExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private SceneExecuteNode sceneExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("章节节点：开始生成章节梗概");
        
        // 从上下文获取VolumePlan
        VolumePlan volumePlan = context.getAttribute("currentVolume");
        
        // 执行章节梗概生成（生成第一章）
        IAgent<Object[], ChapterOutline> chapterAgent = orchestrator.getAgent("ChapterOutlineAgent");
        ChapterOutline outline = chapterAgent.execute(new Object[]{volumePlan, 1}, context);
        
        // 保存到上下文
        context.setAttribute("currentChapter", outline);
        context.setCurrentStage(GenerationStage.SCENE_GENERATION.getName());
        
        return sceneExecuteNode;
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return sceneExecuteNode;
    }
    
}
