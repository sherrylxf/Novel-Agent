package cn.bugstack.novel.domain.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单卷生成完成后的字数与收尾快照，供 SSE 推送与前端确认。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumeCompletionSummary {

    private Integer volumeNumber;
    private String volumeTitle;
    private Integer volumeWordCount;
    private Integer volumeTargetWordCount;
    private Integer bookWordCount;
    private Integer bookTargetWordCount;
    private Integer wordCountTolerance;
    private Boolean volumeWithinTolerance;
    private Boolean bookWithinTolerance;
    private Boolean shouldEndStory;
    private String endingHint;
}
