package cn.bugstack.novel.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说 Agent 配置项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelAgentConfigItem {

    private Long id;

    private String configId;

    private String novelId;

    private String agentType;

    private String configKey;

    private String configValue;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
