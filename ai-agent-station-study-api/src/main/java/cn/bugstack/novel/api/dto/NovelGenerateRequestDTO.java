package cn.bugstack.novel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小说生成请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelGenerateRequestDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 题材（修仙/历史穿越/都市重生/女频言情）
     */
    private String genre;
    
    /**
     * 核心冲突/主题
     */
    private String coreConflict;
    
    /**
     * 世界观设定
     */
    private String worldSetting;
    
    /**
     * 小说ID（可选，如果提供则继续生成）
     */
    private String novelId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 最大执行步数
     */
    private Integer maxStep;
    
    /**
     * 目标总字数（可选，不填则使用默认100万字）
     */
    private Integer targetWordCount;

    /**
     * 继续创作模式：auto=自动完成一章，step=逐步确认
     */
    private String continueMode;
    
}
