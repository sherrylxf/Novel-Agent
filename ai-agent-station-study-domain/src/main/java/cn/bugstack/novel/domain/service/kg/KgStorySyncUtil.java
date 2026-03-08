package cn.bugstack.novel.domain.service.kg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 知识图谱扩展同步工具。
 * 用于为地点、事件、剧情线程生成稳定 ID，避免重复写入。
 */
public final class KgStorySyncUtil {

    private KgStorySyncUtil() {}

    public static String toLocationId(String novelId, String location) {
        return toEntityId(novelId, "loc", location);
    }

    public static String toEventId(String novelId, String eventName) {
        return toEntityId(novelId, "evt", eventName);
    }

    public static String toPlotThreadId(String novelId, String title) {
        return toEntityId(novelId, "plot", title);
    }

    public static String toFactionId(String novelId, String factionName) {
        return toEntityId(novelId, "fac", factionName);
    }

    public static String toArtifactId(String novelId, String artifactName) {
        return toEntityId(novelId, "art", artifactName);
    }

    public static String toTechniqueId(String novelId, String techniqueName) {
        return toEntityId(novelId, "tec", techniqueName);
    }

    public static String toEntityId(String novelId, String prefix, String name) {
        String safeNovelId = novelId != null ? novelId : "";
        String safePrefix = prefix != null ? prefix : "node";
        String safeName = KgCharacterSyncUtil.sanitizeForId(name);
        if (safeName.isEmpty()) {
            safeName = "unknown";
        }
        return safeNovelId + "_" + safePrefix + "_" + safeName;
    }

    public static boolean hasMeaningfulText(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim();
        return !normalized.isEmpty()
                && !"待确定".equals(normalized)
                && !"待完善".equals(normalized)
                && !normalized.contains("待完善");
    }

    public static List<String> distinctNonBlank(List<String> items) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (items != null) {
            for (String item : items) {
                if (item == null) {
                    continue;
                }
                String normalized = item.trim();
                if (!normalized.isEmpty()) {
                    set.add(normalized);
                }
            }
        }
        return new ArrayList<>(set);
    }
}
