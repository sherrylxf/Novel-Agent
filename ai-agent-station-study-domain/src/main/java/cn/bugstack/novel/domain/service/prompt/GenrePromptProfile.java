package cn.bugstack.novel.domain.service.prompt;

import cn.bugstack.novel.types.enums.NovelGenre;

/**
 * 按题材动态加强 System 层「全局设定」，与 {@link FewShotExampleCatalog} 的路由键对齐。
 */
public final class GenrePromptProfile {

    private GenrePromptProfile() {}

    /**
     * @return fewShot 路由键：cultivation / historical / urban / romance / sci_fi / ancient / modern / default
     */
    public static String resolveFewShotProfileKey(Object genre) {
        if (genre instanceof NovelGenre ng) {
            return switch (ng) {
                case CULTIVATION -> "cultivation";
                case HISTORICAL_TRANSMIGRATION -> "historical";
                case URBAN_REBIRTH -> "urban";
                case ROMANCE -> "romance";
                case ANCIENT_ROMANCE -> "ancient";
                case SCI_FI -> "sci_fi";
                case MODERN_URBAN -> "modern";
            };
        }
        String s = genre != null ? genre.toString().trim() : "";
        if (s.contains("科幻")) {
            return "sci_fi";
        }
        if (s.contains("古言")) {
            return "ancient";
        }
        if (s.contains("修仙") || s.contains("玄幻")) {
            return "cultivation";
        }
        if (s.contains("历史") || s.contains("穿越")) {
            return "historical";
        }
        if (s.contains("重生")) {
            return "urban";
        }
        if (s.contains("言情")) {
            return "romance";
        }
        if (s.contains("现代")) {
            return "modern";
        }
        if (s.contains("都市")) {
            return "urban";
        }
        return "default";
    }

    /**
     * 写入 System：文风与世界观基调（短句，避免与用户侧分层重复冗长）。
     */
    public static String systemGenreExtension(Object genre) {
        if (genre instanceof NovelGenre ng) {
            return switch (ng) {
                case CULTIVATION -> "【题材】修仙玄幻：战力与资源逻辑自洽，避免现代口语与科技造物无端出现。";
                case HISTORICAL_TRANSMIGRATION -> "【题材】历史穿越：时代制度、称谓与器物须与背景一致，忌现代网络梗。";
                case URBAN_REBIRTH -> "【题材】都市重生：信息差与节奏偏爽文，现实规则与商业逻辑勿天马行空。";
                case ROMANCE -> "【题材】言情：情绪推进与人物互动优先，冲突围绕关系张力展开。";
                case ANCIENT_ROMANCE -> "【题材】古言：礼制、称谓、闺阁/朝堂语境统一，忌现代价值观硬套。";
                case SCI_FI -> "【题材】科幻：技术边界与社会结构交代清楚，避免魔法式科技。";
                case MODERN_URBAN -> "【题材】现代都市：对话自然、场景写实，节奏可紧凑但忌悬浮霸总套路堆砌。";
            };
        }
        String key = resolveFewShotProfileKey(genre);
        return switch (key) {
            case "sci_fi" -> "【题材】科幻：技术与社会规则自洽。";
            case "ancient" -> "【题材】古言：时代细节与称谓统一。";
            case "modern" -> "【题材】现代都市：写实对话与生活流。";
            default -> "【题材】按全书 Seed 与锚点保持类型一致，忌混用其它类型的典型套路。";
        };
    }
}
