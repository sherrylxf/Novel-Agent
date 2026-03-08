package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 卷/册规划表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VolumePlan {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 卷ID
     */
    private String volumeId;
    
    /**
     * 小说ID
     */
    private String novelId;
    
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
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
}
