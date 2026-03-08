package cn.bugstack.novel.domain.service.rag;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.Scene;

import java.util.Collection;

/**
 * RAG 记忆文档组装工具。
 * 将章节和场景转为更适合检索的摘要型文档，而不是仅存整段正文。
 */
public final class StoryMemoryDocumentUtil {

    private StoryMemoryDocumentUtil() {}

    public static String buildChapterSummaryDocument(ChapterOutline outline) {
        if (outline == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "章节标题", outline.getChapterTitle());
        appendLine(sb, "章节梗概", outline.getOutline());
        appendLine(sb, "关键人物", join(outline.getKeyCharacters(), "、"));
        appendLine(sb, "关键事件", join(outline.getKeyEvents(), "、"));
        appendLine(sb, "伏笔", join(outline.getForeshadowing(), "；"));
        return sb.toString().trim();
    }

    public static String buildSceneSummaryDocument(ChapterOutline outline, Scene scene) {
        if (scene == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "章节标题", outline != null ? outline.getChapterTitle() : "");
        appendLine(sb, "场景标题", scene.getSceneTitle());
        appendLine(sb, "场景类型", scene.getSceneType());
        appendLine(sb, "发生地点", scene.getLocation());
        appendLine(sb, "出场人物", join(scene.getCharacters(), "、"));
        appendLine(sb, "章节梗概", outline != null ? outline.getOutline() : "");
        appendLine(sb, "场景摘要", excerpt(scene.getContent(), 260));
        return sb.toString().trim();
    }

    /** 场景全文用于 embedding 时的最大字符数，避免超出 embedding 模型的 token 上限 */
    private static final int SCENE_FULLTEXT_MAX_CHARS = 2200;

    public static String buildSceneFullTextDocument(ChapterOutline outline, Scene scene) {
        if (scene == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "章节标题", outline != null ? outline.getChapterTitle() : "");
        appendLine(sb, "场景标题", scene.getSceneTitle());
        appendLine(sb, "场景类型", scene.getSceneType());
        appendLine(sb, "发生地点", scene.getLocation());
        appendLine(sb, "出场人物", join(scene.getCharacters(), "、"));
        String content = scene.getContent() != null ? scene.getContent() : "";
        content = excerpt(content, SCENE_FULLTEXT_MAX_CHARS);
        sb.append("场景正文：\n").append(content);
        return sb.toString().trim();
    }

    public static String buildSearchQuery(ChapterOutline outline) {
        if (outline == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendSegment(sb, outline.getChapterTitle());
        appendSegment(sb, outline.getOutline());
        appendSegment(sb, join(outline.getKeyCharacters(), " "));
        appendSegment(sb, join(outline.getKeyEvents(), " "));
        appendSegment(sb, join(outline.getForeshadowing(), " "));
        return sb.toString().trim();
    }

    public static String join(Collection<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(value.trim());
        }
        return sb.toString();
    }

    public static String join(String[] values, String delimiter) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(value.trim());
        }
        return sb.toString();
    }

    public static String excerpt(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(maxLength, 0)) + "...";
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        sb.append(label).append("：").append(value.trim()).append("\n");
    }

    private static void appendSegment(StringBuilder sb, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(value.trim());
    }
}
