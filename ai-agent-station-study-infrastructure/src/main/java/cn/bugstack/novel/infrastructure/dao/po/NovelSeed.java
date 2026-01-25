package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说种子表 PO 对象
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NovelSeed {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 种子ID
     */
    private String seedId;
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 小说标题
     */
    private String title;
    
    /**
     * 题材
     */
    private String genre;
    
    /**
     * 核心冲突/主题
     */
    private String coreConflict;
    
    /**
     * 世界观设定
     */
    private String worldSetting;
    
    /**
     * 主角设定
     */
    private String protagonistSetting;
    
    /**
     * 目标字数
     */
    private Integer targetWordCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
}
