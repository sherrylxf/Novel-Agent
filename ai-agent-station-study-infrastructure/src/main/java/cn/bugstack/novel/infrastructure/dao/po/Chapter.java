package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 章节表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Chapter {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 章节ID
     */
    private String chapterId;

    /**
     * 小说ID
     */
    private String novelId;

    /**
     * 卷序号
     */
    private Integer volumeNumber;

    /**
     * 章节序号
     */
    private Integer chapterNumber;

    /**
     * 章节标题
     */
    private String title;

    /**
     * 章节梗概
     */
    private String outline;

    /**
     * 章节正文
     */
    private String content;

    /**
     * 字数
     */
    private Integer wordCount;

    /**
     * 状态(0:草稿,1:已完成)
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

