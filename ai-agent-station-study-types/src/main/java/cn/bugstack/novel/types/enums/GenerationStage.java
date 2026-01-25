package cn.bugstack.novel.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生成阶段枚举
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Getter
@AllArgsConstructor
public enum GenerationStage {
    
    SEED("种子阶段", "生成小说Seed"),
    NOVEL_PLAN("小说规划", "规划小说整体结构"),
    VOLUME_PLAN("卷/册规划", "规划卷/册结构"),
    CHAPTER_OUTLINE("章节梗概", "生成章节梗概"),
    SCENE_GENERATION("场景生成", "生成场景正文"),
    VALIDATION("校验阶段", "校验生成内容"),
    COMPLETE("完成", "生成完成"),
    
    ;
    
    private final String name;
    private final String description;
    
}
