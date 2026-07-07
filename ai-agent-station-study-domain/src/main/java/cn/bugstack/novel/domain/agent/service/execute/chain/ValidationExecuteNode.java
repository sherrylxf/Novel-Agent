package cn.bugstack.novel.domain.agent.service.execute.chain;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.agent.orchestrator.NovelAgentOrchestrator;
import cn.bugstack.novel.domain.agent.service.execute.AbstractExecuteSupport;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.model.entity.StoryEndingDecision;
import cn.bugstack.novel.domain.model.entity.StoryProgress;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.domain.model.valobj.NovelContextKeys;
import cn.bugstack.novel.domain.model.valobj.VolumeCompletionSummary;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.plot.IPlotTrackerService;
import cn.bugstack.novel.domain.service.plot.StoryPacingPolicy;

import java.util.List;
import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * 校验执行节点
 * 校验生成内容
 */
@Slf4j
@Component
public class ValidationExecuteNode extends AbstractExecuteSupport {
    
    @Resource
    @Lazy
    private NovelAgentOrchestrator orchestrator;
    
    @Resource
    private IPlotTrackerService plotTrackerService;

    @Autowired(required = false)
    private IKnowledgeGraphService knowledgeGraphService;
    
    @Resource
    @Lazy
    private ChapterExecuteNode chapterExecuteNode;
    
    @Resource
    @Lazy
    private VolumeExecuteNode volumeExecuteNode;
    
    @Override
    protected AbstractExecuteSupport doExecute(NovelContext context) {
        log.info("校验节点：开始校验生成内容");
        
        // 从上下文获取Scene
        Scene scene = context.getAttribute("currentScene");
        if (scene == null) {
            log.warn("未找到场景数据，跳过校验");
            context.setCurrentStage(GenerationStage.COMPLETE.name());
            return null;
        }
        accumulateWordCount(context, scene);

        try {
            // 一致性校验
            IAgent<Scene, Boolean> consistencyAgent = orchestrator.getAgent("ConsistencyGuardAgent");
            Boolean consistencyResult = consistencyAgent.execute(scene, context);
            if (!consistencyResult) {
                log.warn("一致性校验失败，但继续执行");
                // 不抛出异常，允许流程继续
            }
            
            // KG规则校验
            IAgent<Scene, Boolean> kgAgent = orchestrator.getAgent("KGRuleValidatorAgent");
            Boolean kgResult = kgAgent.execute(scene, context);
            if (!kgResult) {
                log.warn("知识图谱规则校验失败，但继续执行");
                // 不抛出异常，允许流程继续
            }
            
            log.info("校验完成");
            
            // Plot Tracker + EndingAgent：是否应结束小说（多章循环收束）
            AbstractExecuteSupport nextNode = decideNextAfterValidation(context);
            if (nextNode == null) {
                context.setCurrentStage(GenerationStage.COMPLETE.name());
                log.info("结局判定：应当收束，流程结束");
            } else {
                context.setCurrentStage(nextNode == chapterExecuteNode ? GenerationStage.CHAPTER_OUTLINE.name() : GenerationStage.VOLUME_PLAN.name());
                log.info("结局判定：继续生成下一章/卷，下一节点: {}", nextNode.getClass().getSimpleName());
            }
            return nextNode;
            
        } catch (Exception e) {
            log.error("校验过程出现异常，但继续完成流程", e);
            context.setCurrentStage(GenerationStage.COMPLETE.name());
            return null;
        }
    }
    
    /**
     * 校验完成后：用 Plot Tracker 构建进度 → EndingAgent 判定 → 决定下一节点（下一章/下一卷/结束）
     */
    private AbstractExecuteSupport decideNextAfterValidation(NovelContext context) {
        try {
            NovelPlan plan = context.getAttribute("plan");
            String storySummary = plan != null && plan.getOverallOutline() != null
                    ? plan.getOverallOutline().length() > 2000
                    ? plan.getOverallOutline().substring(0, 2000) + "..."
                    : plan.getOverallOutline()
                    : "";
            StoryProgress progress = plotTrackerService.buildProgress(context, storySummary);
            IAgent<StoryProgress, StoryEndingDecision> endingAgent = orchestrator.getAgent("EndingAgent");
            StoryEndingDecision decision = endingAgent.execute(progress, context);
            if (decision.isShouldEndStory()) {
                log.info("EndingAgent: shouldEndStory=true, reason={}", decision.getReason());
                recordVolumeCompletion(context, true, decision.getReason());
                try {
                    if (knowledgeGraphService != null) {
                        List<String> toResolve = knowledgeGraphService.listUnresolvedForeshadowingIds(context.getNovelId());
                        if (!toResolve.isEmpty()) {
                            knowledgeGraphService.resolveForeshadowings(context.getNovelId(), toResolve);
                            log.info("结局时批量回收伏笔: novelId={}, count={}", context.getNovelId(), toResolve.size());
                        }
                    }
                } catch (Exception e) {
                    log.warn("批量回收伏笔失败，不阻塞流程: {}", e.getMessage());
                }
                return null;
            }
            // 计算下一章/下一卷
            VolumePlan currentVolume = context.getAttribute("currentVolume");
            Integer currentVolumeNumber = context.getAttribute("currentVolumeNumber");
            Integer currentChapterInVolume = context.getAttribute("currentChapterInVolume");
            if (currentVolumeNumber == null) currentVolumeNumber = 1;
            if (currentChapterInVolume == null) currentChapterInVolume = 1;
            int chapterCount = currentVolume != null && currentVolume.getChapterCount() != null
                    ? currentVolume.getChapterCount()
                    : (plan != null && plan.getChaptersPerVolume() != null ? plan.getChaptersPerVolume() : 20);
            int totalVolumes = plan != null && plan.getTotalVolumes() != null ? plan.getTotalVolumes() : 1;
            if (currentChapterInVolume < chapterCount) {
                context.setAttribute("currentChapterInVolume", currentChapterInVolume + 1);
                return chapterExecuteNode;
            }
            if (currentVolumeNumber < totalVolumes) {
                recordVolumeCompletion(context, false, null);
                context.setAttribute("currentVolumeNumber", currentVolumeNumber + 1);
                context.setAttribute("currentChapterInVolume", 1);
                resetVolumeWordCount(context);
                return volumeExecuteNode;
            }
            log.info("已到规划最后一章/卷，流程结束");
            recordVolumeCompletion(context, true, "已到规划最后一卷最后一章");
            return null;
        } catch (Exception e) {
            log.warn("结局判定异常，默认结束本流程", e);
            return null;
        }
    }
    
    @Override
    public AbstractExecuteSupport getNext(NovelContext context) {
        return null;
    }

    private void accumulateWordCount(NovelContext context, Scene scene) {
        if (context == null || scene == null) {
            return;
        }
        Integer currentChapterInVolume = context.getAttribute("currentChapterInVolume");
        Integer countedChapterInVolume = context.getAttribute("countedChapterInVolume");
        Integer currentVolumeNumber = context.getAttribute("currentVolumeNumber");
        Integer countedVolumeNumber = context.getAttribute("countedVolumeNumber");
        if (currentChapterInVolume != null && currentChapterInVolume.equals(countedChapterInVolume)
                && currentVolumeNumber != null && currentVolumeNumber.equals(countedVolumeNumber)) {
            return;
        }
        Integer accumulated = context.getAttribute("accumulatedWordCount");
        if (accumulated == null) {
            accumulated = 0;
        }
        int sceneWords = scene.getWordCount() != null ? scene.getWordCount() : 0;
        if (sceneWords <= 0 && scene.getContent() != null) {
            sceneWords = scene.getContent().length();
        }
        context.setAttribute("accumulatedWordCount", accumulated + Math.max(0, sceneWords));
        accumulateVolumeWordCount(context, currentVolumeNumber, sceneWords);
        context.setAttribute("countedChapterInVolume", currentChapterInVolume);
        context.setAttribute("countedVolumeNumber", currentVolumeNumber);
    }

    private void accumulateVolumeWordCount(NovelContext context, Integer currentVolumeNumber, int sceneWords) {
        if (context == null || currentVolumeNumber == null) {
            return;
        }
        Integer trackedVolume = context.getAttribute("volumeWordCountVolume");
        Integer volumeWords = context.getAttribute("currentVolumeWordCount");
        if (volumeWords == null || !currentVolumeNumber.equals(trackedVolume)) {
            volumeWords = 0;
        }
        context.setAttribute("currentVolumeWordCount", volumeWords + Math.max(0, sceneWords));
        context.setAttribute("volumeWordCountVolume", currentVolumeNumber);
    }

    private void resetVolumeWordCount(NovelContext context) {
        if (context == null) {
            return;
        }
        context.setAttribute("currentVolumeWordCount", 0);
        context.removeAttribute("volumeWordCountVolume");
    }

    private void recordVolumeCompletion(NovelContext context, boolean shouldEndStory, String endingHint) {
        if (context == null) {
            return;
        }
        VolumePlan volume = context.getAttribute("currentVolume");
        Integer volumeNumber = context.getAttribute("currentVolumeNumber");
        Integer volumeWords = context.getAttribute("currentVolumeWordCount");
        Integer bookWords = context.getAttribute("accumulatedWordCount");
        int volumeTarget = StoryPacingPolicy.resolveVolumeTargetWordCount(context);
        int bookTarget = StoryPacingPolicy.resolveTargetWordCount(context, null);
        int tolerance = StoryPacingPolicy.resolveWordCountTolerance(context);

        boolean volumeOk = volumeTarget <= 0 || volumeWords == null
                || Math.abs(volumeWords - volumeTarget) <= tolerance;
        boolean bookOk = bookTarget <= 0 || bookWords == null
                || (bookWords >= bookTarget - tolerance && bookWords <= bookTarget + tolerance);

        VolumeCompletionSummary summary = VolumeCompletionSummary.builder()
                .volumeNumber(volumeNumber)
                .volumeTitle(volume != null ? volume.getVolumeTitle() : null)
                .volumeWordCount(volumeWords)
                .volumeTargetWordCount(volumeTarget > 0 ? volumeTarget : null)
                .bookWordCount(bookWords)
                .bookTargetWordCount(bookTarget > 0 ? bookTarget : null)
                .wordCountTolerance(tolerance)
                .volumeWithinTolerance(volumeOk)
                .bookWithinTolerance(bookOk)
                .shouldEndStory(shouldEndStory)
                .endingHint(endingHint)
                .build();
        context.setAttribute(NovelContextKeys.LAST_VOLUME_COMPLETION, summary);
    }
    
}
