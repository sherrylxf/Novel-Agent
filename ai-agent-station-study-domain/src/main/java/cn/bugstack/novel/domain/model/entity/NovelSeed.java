package cn.bugstack.novel.domain.model.entity;

import cn.bugstack.novel.types.enums.NovelGenre;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 小说种子
 * 小说的初始设定和核心概念
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelSeed {
    
    /**
     * 种子ID
     */
    private String seedId;
    
    /**
     * 小说标题
     */
    private String title;
    
    /**
     * 题材
     */
    private NovelGenre genre;
    
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
    
}
