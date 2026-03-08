package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.StoryEndingDecision;
import cn.bugstack.novel.domain.model.entity.StoryProgress;
import cn.bugstack.novel.domain.service.llm.ILLMClient;
import cn.bugstack.novel.types.enums.AgentType;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * 结局判定 Agent
 *
 * 输入：剧情概要 + 未解决伏笔 + 章节进度
 * 输出：是否应该结束小说（shouldEndStory）
 */
@Slf4j
@Component
public class EndingAgent extends AbstractAgent<StoryProgress, StoryEndingDecision> {

    private final ILLMClient llmClient;

    public EndingAgent(ILLMClient llmClient) {
        super(AgentType.STORY_ENDING);
        this.llmClient = llmClient;
    }

    @Override
    protected StoryEndingDecision doExecute(StoryProgress progress, NovelContext context) {
        log.info("结局判定：当前章节 {}, 规划总章节 {}", progress.getCurrentChapterNumber(), progress.getPlannedTotalChapters());

        try {
            if (llmClient == null) {
                log.warn("未配置 ILLMClient，使用启发式降级策略进行结局判定");
                return heuristicDecision(progress);
            }

            StoryEndingDecision decision = llmDecision(progress);
            log.info("结局判定完成，shouldEndStory={}, confidence={}",
                    decision.isShouldEndStory(), decision.getConfidence());
            return decision;
        } catch (Exception e) {
            log.error("结局判定失败，使用启发式降级策略", e);
            return heuristicDecision(progress);
        }
    }

    /**
     * 使用 LLM 进行结局判定
     */
    private StoryEndingDecision llmDecision(StoryProgress progress) {
        String systemPrompt = "你是一个资深网文编辑，也是小说结构控制专家，擅长判断一本连载小说是否应该收束结局。\n"
                + "根据给定的剧情摘要、未解决伏笔列表以及章节进度，判断当前是否应该进入结局阶段，避免小说无限拖长。"
                + "只关注结构和伏笔回收，不必纠结具体文风。";

        // 使用字符串拼接构建 prompt，避免 StringTemplate 解析 storySummary 中的 { }、true 等字符导致报错
        String foreshadowingText;
        if (progress.getUnresolvedForeshadowings() == null
                || progress.getUnresolvedForeshadowings().isEmpty()) {
            foreshadowingText = "（无明显未解决伏笔，或未提供伏笔列表）";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < progress.getUnresolvedForeshadowings().size(); i++) {
                sb.append(i + 1)
                        .append(". ")
                        .append(progress.getUnresolvedForeshadowings().get(i))
                        .append("\n");
            }
            foreshadowingText = sb.toString();
        }
        String plotThreadText;
        if (progress.getActivePlotThreads() == null || progress.getActivePlotThreads().isEmpty()) {
            plotThreadText = "（当前没有活跃剧情线程，或尚未提取）";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < progress.getActivePlotThreads().size(); i++) {
                sb.append(i + 1)
                        .append(". ")
                        .append(progress.getActivePlotThreads().get(i))
                        .append("\n");
            }
            plotThreadText = sb.toString();
        }
        String userPrompt = "【小说剧情摘要】\n"
                + (progress.getStorySummary() != null ? progress.getStorySummary() : "")
                + "\n\n【当前进度】\n"
                + "- 当前章节：第 " + progress.getCurrentChapterNumber() + " 章\n"
                + "- 规划总章节数：" + progress.getPlannedTotalChapters() + "\n\n"
                + "【未解决伏笔列表】（可能为空）\n"
                + foreshadowingText
                + "\n【活跃剧情线程】\n"
                + plotThreadText
                + "\n请你综合判断：\n"
                + "1. 主线冲突是否已经基本解决？\n"
                + "2. 核心人物的成长弧线是否已经完成？\n"
                + "3. 关键伏笔和剧情线程是否已经大部分回收？\n"
                + "4. 当前节奏是否已经接近或超过合理篇幅？\n\n"
                + "请严格按照以下 JSON 格式输出，不要添加任何额外文字：\n"
                + "{\n"
                + "  \"shouldEndStory\": true 或 false,\n"
                + "  \"reason\": \"用一两句话说明主要判断依据\",\n"
                + "  \"confidence\": 0 到 1 之间的小数\n"
                + "}";

        String llmResponse = llmClient.call(systemPrompt, userPrompt);
        String jsonStr = extractJsonFromResponse(llmResponse);

        JSONObject json = JSON.parseObject(jsonStr);
        boolean shouldEnd = Boolean.TRUE.equals(json.getBoolean("shouldEndStory"));
        String reason = json.getString("reason");
        Double confidence = json.getDouble("confidence");

        if (confidence == null) {
            confidence = 0.5d;
        }

        return StoryEndingDecision.builder()
                .shouldEndStory(shouldEnd)
                .reason(reason != null ? reason : "")
                .confidence(confidence)
                .build();
    }

    /**
     * 简单启发式结局判定（LLM 不可用时）
     */
    private StoryEndingDecision heuristicDecision(StoryProgress progress) {
        Integer current = progress.getCurrentChapterNumber();
        Integer planned = progress.getPlannedTotalChapters();
        int unresolvedCount = progress.getUnresolvedForeshadowings() != null
                ? progress.getUnresolvedForeshadowings().size() : 0;
        int activeThreadCount = progress.getActivePlotThreads() != null
                ? progress.getActivePlotThreads().size() : 0;

        boolean shouldEnd = false;
        StringBuilder reason = new StringBuilder();

        if (planned != null && current != null) {
            if (current >= planned) {
                shouldEnd = unresolvedCount == 0 && activeThreadCount == 0;
                reason.append("已达到规划的总章节数；");
            } else if (current >= Math.max(1, (int) (planned * 0.8)) && unresolvedCount == 0 && activeThreadCount == 0) {
                shouldEnd = true;
                reason.append("已接近规划尾声且无明显未解决伏笔或活跃剧情线程；");
            } else {
                shouldEnd = false;
                reason.append("尚未到规划末尾，或仍有未解决伏笔/活跃剧情线程；");
            }
        } else {
            // 没有规划信息时，只根据伏笔和剧情线程做保守判断
            shouldEnd = unresolvedCount == 0 && activeThreadCount == 0;
            if (shouldEnd) {
                reason.append("未提供规划总章节，但当前无未解决伏笔和活跃剧情线程，建议收束；");
            } else {
                reason.append("未提供规划总章节，且仍有未解决伏笔或活跃剧情线程，暂不结束；");
            }
        }

        return StoryEndingDecision.builder()
                .shouldEndStory(shouldEnd)
                .reason(reason.toString())
                .confidence(0.4d)
                .build();
    }

    /**
     * 从 LLM 响应中提取 JSON 片段
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) {
            return "{}";
        }
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}

