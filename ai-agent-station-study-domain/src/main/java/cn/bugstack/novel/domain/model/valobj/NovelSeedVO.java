package cn.bugstack.novel.domain.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说Seed值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelSeedVO {
    
    private String seedId;
    private String novelId;
    private String title;
    private String genre;
    private String coreConflict;
    private String worldSetting;
    private String protagonistSetting;
    private Integer targetWordCount;
    
}
