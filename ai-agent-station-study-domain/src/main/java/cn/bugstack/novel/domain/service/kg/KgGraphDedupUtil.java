package cn.bugstack.novel.domain.service.kg;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 知识图谱节点去重工具
 * 为事件、伏笔、剧情线等提供规范键，与人物节点一致地合并重复节点。
 */
public final class KgGraphDedupUtil {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int CONTENT_KEY_LEN = 120;
    private static final int LABEL_KEY_LEN = 80;

    private KgGraphDedupUtil() {}

    /**
     * 伏笔/长文本的规范键：缩空格、截断长度，用于合并内容相近的节点
     */
    public static String canonicalContentKey(String content) {
        if (content == null) return "";
        String s = WHITESPACE.matcher(content.trim()).replaceAll(" ");
        if (s.isEmpty()) return "";
        return s.length() <= CONTENT_KEY_LEN ? s : s.substring(0, CONTENT_KEY_LEN);
    }

    /**
     * 事件/剧情线/标题类标签的规范键：去括号内内容、缩空格、截断，用于合并标题相近的节点
     */
    public static String canonicalLabelKey(String label) {
        if (label == null || label.isEmpty()) return "";
        String s = label.trim();
        int p = s.indexOf("（");
        if (p < 0) p = s.indexOf("(");
        if (p > 0) s = s.substring(0, p).trim();
        s = WHITESPACE.matcher(s).replaceAll(" ");
        if (s.endsWith("…") || s.endsWith("...")) s = s.replaceAll("[….]++$", "").trim();
        if (s.isEmpty()) return "";
        return s.length() <= LABEL_KEY_LEN ? s : s.substring(0, LABEL_KEY_LEN);
    }

    /**
     * 按节点类型计算去重用的规范键；同类型+同键的节点在展示时合并为一个。
     */
    public static String canonicalKeyForNode(String type, String label, Map<String, Object> properties) {
        if (type == null) type = "";
        String content = null;
        if (properties != null && properties.get("content") != null) {
            content = String.valueOf(properties.get("content")).trim();
        }
        String name = (label != null && !label.isEmpty()) ? label : (properties != null && properties.get("name") != null ? String.valueOf(properties.get("name")) : "");

        switch (type) {
            case "Character":
                String c = KgCharacterSyncUtil.canonicalName(name);
                return c != null && !c.isEmpty() ? c : name;
            case "Foreshadowing":
                String cont = (content != null && !content.isEmpty()) ? content : name;
                return canonicalContentKey(cont);
            case "Event":
            case "PlotThread":
            case "Location":
            case "Faction":
            case "Artifact":
            case "Technique":
            default:
                return canonicalLabelKey(name.isEmpty() ? label : name);
        }
    }
}
