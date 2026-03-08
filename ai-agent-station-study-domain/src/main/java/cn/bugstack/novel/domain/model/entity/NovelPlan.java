package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 小说规划
 * 小说的整体结构规划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelPlan {
    
    /**
     * 规划ID
     */
    private String planId;
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 总卷数
     */
    private Integer totalVolumes;
    
    /**
     * 每卷章节数
     */
    private Integer chaptersPerVolume;
    
    /**
     * 卷规划列表
     */
    private List<VolumePlan> volumePlans;
    
    /**
     * 整体大纲
     */
    private String overallOutline;
    
}
