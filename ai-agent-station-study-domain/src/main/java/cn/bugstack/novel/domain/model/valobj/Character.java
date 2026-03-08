package cn.bugstack.novel.domain.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 人物值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Character {
    
    /**
     * 人物ID
     */
    private String characterId;

    /**
     * 所属小说ID（用于按小说筛选图谱，可选）
     */
    private String novelId;
    
    /**
     * 人物名称
     */
    private String name;
    
    /**
     * 人物类型（主角/配角/反派等）
     */
    private String type;
    
    /**
     * 性格特征
     */
    private String personality;
    
    /**
     * 背景设定
     */
    private String background;
    
    /**
     * 能力/修为
     */
    private String abilities;
    
    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;
    
}
