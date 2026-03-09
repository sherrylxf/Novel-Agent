package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说工作台聚合对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelProject {

    private Long id;

    private String novelId;

    private String title;

    private String genre;

    private Integer status;

    private String coreConflict;

    private String worldSetting;

    private String latestPlanId;

    private Integer totalVolumes;

    private Integer chaptersPerVolume;

    private Integer chapterCount;

    private Boolean hasPlan;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
