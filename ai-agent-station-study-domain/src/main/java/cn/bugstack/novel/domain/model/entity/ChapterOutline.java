package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 章节梗概
 * 章节的梗概和场景规划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterOutline {
    
    /**
     * 章节ID
     */
    private String chapterId;
    
    /**
     * 章节序号
     */
    private Integer chapterNumber;
    
    /**
     * 章节标题
     */
    private String chapterTitle;
    
    /**
     * 章节梗概
     */
    private String outline;
    
    /**
     * 关键人物
     */
    private List<String> keyCharacters;
    
    /**
     * 关键事件
     */
    private List<String> keyEvents;
    
    /**
     * 伏笔
     */
    private List<String> foreshadowing;
    
    /**
     * 场景列表
     */
    private List<Scene> scenes;
    
}
