package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDetail {

    private String chapterId;

    private String novelId;

    private Integer volumeNumber;

    private Integer chapterNumber;

    private String title;

    private String outline;

    private String content;

    private Integer wordCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<SceneDetail> scenes;
}
