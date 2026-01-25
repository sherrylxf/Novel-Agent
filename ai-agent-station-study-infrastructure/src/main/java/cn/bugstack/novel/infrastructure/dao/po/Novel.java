package cn.bugstack.novel.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 小说表 PO 对象
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Novel {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 小说ID
     */
    private String novelId;
    
    /**
     * 小说标题
     */
    private String title;
    
    /**
     * 题材
     */
    private String genre;
    
    /**
     * 状态(0:禁用,1:启用)
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
}
