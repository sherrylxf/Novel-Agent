package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 卷/册规划
 * 一卷或一册的结构规划
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolumePlan {
    
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
    
    /**
     * 章节规划列表
     */
    private List<ChapterOutline> chapterOutlines;
    
}
