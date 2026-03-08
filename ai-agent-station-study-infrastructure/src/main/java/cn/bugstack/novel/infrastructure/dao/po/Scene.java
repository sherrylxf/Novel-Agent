package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场景表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Scene {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 场景ID
     */
    private String sceneId;

    /**
     * 章节ID
     */
    private String chapterId;

    /**
     * 场景序号
     */
    private Integer sceneNumber;

    /**
     * 场景标题
     */
    private String sceneTitle;

    /**
     * 场景类型
     */
    private String sceneType;

    /**
     * 场景正文
     */
    private String content;

    /**
     * 字数
     */
    private Integer wordCount;

    /**
     * 参与人物（JSON数组字符串）
     */
    private String characters;

    /**
     * 地点
     */
    private String location;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

