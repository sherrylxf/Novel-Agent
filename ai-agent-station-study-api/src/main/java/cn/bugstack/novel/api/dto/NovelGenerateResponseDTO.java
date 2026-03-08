package cn.bugstack.novel.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小说生成响应DTO（SSE格式）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelGenerateResponseDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息类型（progress/result/waiting_for_approval/complete/error）
     * - progress: 进度消息
     * - result: 结果消息（已废弃，使用waiting_for_approval）
     * - waiting_for_approval: 等待用户确认（包含生成的内容数据）
     * - complete: 完成消息
     * - error: 错误消息
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
    
    /**
     * 创建等待用户确认消息
     * 节点执行完成后，发送生成的内容给前端，等待用户确认后继续
     * 
     * @param sessionId 会话ID
     * @param stage 当前阶段（seed/plan/volume/chapter/scene）
     * @param nodeName 节点名称（用于前端显示）
     * @param data 生成的内容数据（NovelSeed/NovelPlan/VolumePlan/ChapterOutline/Scene等）
     * @param nextStage 下一个阶段（用于前端显示）
     */
    public static NovelGenerateResponseDTO waitingForApproval(String sessionId, String stage, 
                                                               String nodeName, Object data, String nextStage) {
        return NovelGenerateResponseDTO.builder()
                .type("waiting_for_approval")
                .sessionId(sessionId)
                .stage(stage)
                .content(String.format("节点[%s]执行完成，等待用户确认后继续执行[%s]", nodeName, nextStage))
                .data(data)
                .build();
    }
    
}
