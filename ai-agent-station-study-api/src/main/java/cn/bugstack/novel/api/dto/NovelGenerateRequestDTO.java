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

    /**
     * 生成模式：step=分步确认（默认），full=一键生成整本（不等待用户确认）
     */
    private String generateMode;

    /**
     * 总章节数（一键整本时使用，与 wordsPerChapter 配合）
     */
    private Integer chaptersTotal;

    /**
     * 每章字数（一键整本时使用，默认3000）
     */
    private Integer wordsPerChapter;

    /**
     * 批量继续创作时，本批次要创建的章节数（与 continueMode=batch 配合）
     */
    private Integer chaptersToCreate;

    /**
     * 用户总控 Prompt（完整创作约束，优先于 genre/coreConflict/worldSetting 片段）
     */
    private String masterPrompt;

    /**
     * 规划总卷数（如 5 卷 × 10 万字）
     */
    private Integer totalVolumes;

    /**
     * 单卷目标字数（如每卷 100000 字）
     */
    private Integer volumeTargetWordCount;

    /**
     * 全书目标字数允许误差（默认 10000，即 50 万字 ±1 万）
     */
    private Integer wordCountTolerance;

}
