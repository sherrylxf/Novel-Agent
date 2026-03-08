package cn.bugstack.novel.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 小说题材枚举
 */
@Getter
@AllArgsConstructor
public enum NovelGenre {
    
    CULTIVATION("修仙", "修仙题材，包含修炼、突破、法宝等元素"),
    HISTORICAL_TRANSMIGRATION("历史穿越", "历史穿越题材，主角穿越到古代"),
    URBAN_REBIRTH("都市重生", "都市重生题材，主角重生到过去"),
    ROMANCE("女频言情", "女频言情题材，以感情线为主"),
    
    ;
    
    private final String name;
    private final String description;
    
}
