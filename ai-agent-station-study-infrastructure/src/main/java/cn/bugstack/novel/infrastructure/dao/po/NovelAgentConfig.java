package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说 Agent 配置表 PO 对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NovelAgentConfig {

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
