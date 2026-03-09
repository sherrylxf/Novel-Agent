package cn.bugstack.novel.domain.service.kg;

import cn.bugstack.novel.domain.model.valobj.Character;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 知识图谱人物同步工具
 * 用于从小说种子、章节梗概、场景中统一生成人物ID并构建 Character 对象，避免图谱为空。
 */
public final class KgCharacterSyncUtil {

    private static final Pattern INVALID_ID = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5_]");

    private KgCharacterSyncUtil() {}

    /**
     * 人物名规范化为“核心名”，用于去重：同一人物不同写法（如 主角'林衍' / 林衍、苏砚（穿越后原名）... / 苏砚）映射到同一节点。
     */
    public static String canonicalName(String name) {
        if (name == null || name.isEmpty()) return "";
        String s = name.trim();
        // 去掉 主角'xxx' 或 主角"xxx" 的外层
        if (s.startsWith("主角'") && s.length() > 3 && s.indexOf("'", 3) > 0) {
            int end = s.indexOf("'", 3);
            s = s.substring(3, end).trim();
        } else if (s.startsWith("主角\"") && s.length() > 3 && s.indexOf("\"", 3) > 0) {
            int end = s.indexOf("\"", 3);
            s = s.substring(3, end).trim();
        }
        // 去掉全角括号及其中内容：苏砚（穿越后原名） -> 苏砚
        int p = s.indexOf("（");
        if (p < 0) p = s.indexOf("(");
        if (p > 0) s = s.substring(0, p).trim();
        // 去掉句号后的补充说明：xxx。年龄：28岁 -> xxx
        int dot = s.indexOf("。");
        if (dot > 0) s = s.substring(0, dot).trim();
        return s.isEmpty() ? name.trim() : s;
    }

    /**
     * 生成稳定的人物ID（同一小说下同一规范名唯一，避免重复节点）
     * 使用规范名生成 ID，仅保留字母、数字、中文、下划线。
     */
    public static String toCharacterId(String novelId, String name) {
        if (novelId == null) novelId = "";
        String canonical = canonicalName(name);
        String safe = sanitizeForId(canonical);
        if (safe.isEmpty()) safe = sanitizeForId(name);
        if (safe.isEmpty()) safe = "unknown";
        return novelId + "_c_" + safe;
    }

    /**
     * 对名称做规范化，用于作为 ID 的一部分（保留中文、英文、数字、下划线）
     */
    public static String sanitizeForId(String name) {
        if (name == null || name.isEmpty()) return "";
        String t = name.trim();
        return INVALID_ID.matcher(t).replaceAll("_").replaceAll("_+", "_");
    }

    /**
     * 从主角设定文本中简单解析主角名称（用于 Seed 阶段）
     * 若无法解析则返回 "主角"，背景用全文。
     */
    public static String parseProtagonistName(String protagonistSetting) {
        if (protagonistSetting == null || protagonistSetting.isEmpty()) return "主角";
        String s = protagonistSetting.trim();
        // 常见模式：姓名：xxx / 名字：xxx / 主角：xxx / 主角名xxx
        for (String prefix : new String[]{"姓名：", "姓名:", "名字：", "名字:", "主角：", "主角:", "主角名"}) {
            int i = s.indexOf(prefix);
            if (i >= 0) {
                String rest = s.substring(i + prefix.length()).trim();
                int end = rest.indexOf("，");
                if (end < 0) end = rest.indexOf(",");
                if (end < 0) end = rest.indexOf("。");
                if (end < 0) end = rest.indexOf(" ");
                if (end > 0) rest = rest.substring(0, end).trim();
                if (!rest.isEmpty()) return rest;
            }
        }
        // 取第一句或前若干字作为“名字”的近似（很多设定是「张三，性格...」）
        int comma = s.indexOf("，");
        if (comma < 0) comma = s.indexOf(",");
        if (comma > 0 && comma <= 10) return s.substring(0, comma).trim();
        if (s.length() <= 6) return s;
        return "主角";
    }

    /**
     * 为章节/场景中出现的人物构建 Character 列表（用于 MERGE 写入 KG）
     * 使用规范名作为节点 name，与 toCharacterId 一致，避免同一人物多节点重复。
     */
    public static List<Character> buildCharactersForSync(String novelId, List<String> names, String defaultType) {
        List<Character> list = new ArrayList<>();
        if (names == null) return list;
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) continue;
            String n = name.trim();
            String canonical = canonicalName(n);
            list.add(Character.builder()
                    .characterId(toCharacterId(novelId, n))
                    .name(canonical.isEmpty() ? n : canonical)
                    .novelId(novelId != null ? novelId : "")
                    .type(defaultType != null ? defaultType : "配角")
                    .personality("")
                    .background("")
                    .abilities("")
                    .build());
        }
        return list;
    }

    /**
     * 从场景的 characters 数组构建 Character 列表
     */
    public static List<Character> buildCharactersFromScene(String novelId, String[] characters, String defaultType) {
        List<String> names = new ArrayList<>();
        if (characters != null) {
            for (String c : characters) {
                if (c != null && !c.trim().isEmpty()) names.add(c.trim());
            }
        }
        return buildCharactersForSync(novelId, names, defaultType);
    }
}
