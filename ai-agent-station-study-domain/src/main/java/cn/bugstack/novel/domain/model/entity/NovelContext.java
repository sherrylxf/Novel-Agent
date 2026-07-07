package cn.bugstack.novel.domain.model.entity;

import cn.bugstack.novel.types.enums.PipelineExecutionState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 小说上下文
 * 在Agent之间传递的共享上下文对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelContext {
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 当前生成阶段
     */
    private String currentStage;

    /**
     * Pipeline 执行生命周期（待调度/执行中/任务成功/任务失败等），与 {@link #currentStage} 业务阶段正交。
     */
    @Builder.Default
    private PipelineExecutionState pipelineExecutionState = PipelineExecutionState.PENDING;
    
    /**
     * 扩展属性（用于存储临时数据）
     */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
    
    /**
     * 设置属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 移除扩展属性（例如清除上一阶段的失败信息）。
     */
    public void removeAttribute(String key) {
        if (attributes != null) {
            attributes.remove(key);
        }
    }
    
    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }
    
    /**
     * 获取属性（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, T defaultValue) {
        return (T) attributes.getOrDefault(key, defaultValue);
    }
    
}
