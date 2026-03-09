package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneDetail {

    private String sceneId;

    private Integer sceneNumber;

    private String sceneTitle;

    private String sceneType;

    private String content;

    private Integer wordCount;

    private String characters;

    private String location;
}
