package cn.bugstack.novel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小说生成响应DTO（SSE格式）
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelGenerateResponseDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息类型（progress/result/complete/error）
     */
    private String type;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 当前阶段
     */
    private String stage;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 数据（JSON格式）
     */
    private Object data;
    
    /**
     * 创建进度消息
     */
    public static NovelGenerateResponseDTO progress(String sessionId, String stage, String content) {
        return NovelGenerateResponseDTO.builder()
                .type("progress")
                .sessionId(sessionId)
                .stage(stage)
                .content(content)
                .build();
    }
    
    /**
     * 创建结果消息
     */
    public static NovelGenerateResponseDTO result(String sessionId, String stage, Object data) {
        return NovelGenerateResponseDTO.builder()
                .type("result")
                .sessionId(sessionId)
                .stage(stage)
                .data(data)
                .build();
    }
    
    /**
     * 创建完成消息
     */
    public static NovelGenerateResponseDTO complete(String sessionId, String novelId) {
        return NovelGenerateResponseDTO.builder()
                .type("complete")
                .sessionId(sessionId)
                .novelId(novelId)
                .content("小说生成完成")
                .build();
    }
    
    /**
     * 创建错误消息
     */
    public static NovelGenerateResponseDTO error(String sessionId, String errorMessage) {
        return NovelGenerateResponseDTO.builder()
                .type("error")
                .sessionId(sessionId)
                .content(errorMessage)
                .build();
    }
    
}
