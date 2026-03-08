package cn.bugstack.novel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 保存小说规划请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelPlanSaveRequestDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
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
     * 整体大纲
     */
    private String overallOutline;
    
    /**
     * 卷规划列表
     */
    private List<VolumePlanDTO> volumePlans;
    
    /**
     * 卷规划DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumePlanDTO implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;
        
        /**
         * 卷ID
         */
        private String volumeId;
        
        /**
         * 卷序号
         */
        private Integer volumeNumber;
        
        /**
         * 卷标题
         */
        private String volumeTitle;
        
        /**
         * 卷主题
         */
        private String volumeTheme;
        
        /**
         * 章节数
         */
        private Integer chapterCount;
        
    }
    
}
