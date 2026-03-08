package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 故事结局判定结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryEndingDecision {

    /**
     * 是否应该结束当前小说（true 表示应当在当前或下一章收束主线）
     */
    private boolean shouldEndStory;

    /**
     * 判定原因说明，便于在前端展示和调试
     */
    private String reason;

    /**
     * 判定置信度（0-1），仅作参考
     */
    private Double confidence;
}

