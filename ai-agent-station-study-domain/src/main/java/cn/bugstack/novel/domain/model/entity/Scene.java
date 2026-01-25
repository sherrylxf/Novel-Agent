package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景
 * 小说的具体场景内容
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scene {
    
    /**
     * 场景ID
     */
    private String sceneId;
    
    /**
     * 场景序号
     */
    private Integer sceneNumber;
    
    /**
     * 场景标题
     */
    private String sceneTitle;
    
    /**
     * 场景类型（对话/战斗/日常/情感等）
     */
    private String sceneType;
    
    /**
     * 场景正文
     */
    private String content;
    
    /**
     * 场景字数
     */
    private Integer wordCount;
    
    /**
     * 参与人物
     */
    private String[] characters;
    
    /**
     * 地点
     */
    private String location;
    
}
