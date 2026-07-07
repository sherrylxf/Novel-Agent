package cn.bugstack.novel.domain.service.rag;

import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.StoryKnowledgeSnapshot;
import cn.bugstack.novel.domain.service.kg.KgStorySyncUtil;

import java.util.LinkedHashSet;

/**
 * 检索 Query 改写：在向量查询侧拼接任务意图与实体线索，提升召回与当前章节的相关性（非用户原始一句式输入）。
 */
public final class StoryRetrievalQueryRewriter {

    private StoryRetrievalQueryRewriter() {}

    public static String rewriteForSceneRetrieval(ChapterOutline outline,
                                                  NovelContext context,
                                                  StoryKnowledgeSnapshot knowledgeSnapshot) {
        String base = StoryMemoryDocumentUtil.buildSearchQuery(outline);
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        hints.add("小说章节写作参考");
        hints.add("连续性记忆检索");
        if (outline != null && outline.getChapterNumber() != null) {
            hints.add("第" + outline.getChapterNumber() + "章");
        }
        if (context != null && KgStorySyncUtil.hasMeaningfulText(context.getNovelId())) {
            hints.add("限定本书");
        }
        if (outline != null) {
            hints.addAll(KgStorySyncUtil.distinctNonBlank(outline.getKeyCharacters()));
            hints.addAll(KgStorySyncUtil.distinctNonBlank(outline.getKeyEvents()));
        }
        if (knowledgeSnapshot != null && knowledgeSnapshot.getActivePlotThreads() != null) {
            hints.addAll(KgStorySyncUtil.distinctNonBlank(knowledgeSnapshot.getActivePlotThreads()));
        }
        StringBuilder sb = new StringBuilder();
        for (String h : hints) {
            if (h == null || h.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(h.trim());
        }
        if (KgStorySyncUtil.hasMeaningfulText(base)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(base.trim());
        }
        return sb.toString().trim();
    }
}
