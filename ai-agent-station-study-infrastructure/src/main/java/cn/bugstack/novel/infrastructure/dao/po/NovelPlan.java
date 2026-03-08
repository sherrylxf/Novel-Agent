package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说规划表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NovelPlan {
    
    /**
     * 主键ID
     */
    private Long id;
    
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
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
}
