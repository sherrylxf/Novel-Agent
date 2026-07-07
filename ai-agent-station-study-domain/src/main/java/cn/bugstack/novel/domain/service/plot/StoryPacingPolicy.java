package cn.bugstack.novel.domain.service.plot;

import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.NovelPlan;
import cn.bugstack.novel.domain.model.entity.NovelSeed;
import cn.bugstack.novel.domain.model.entity.VolumePlan;

/**
 * Shared pacing rules for long-form generation.
 */
public final class StoryPacingPolicy {

    public static final int DEFAULT_TARGET_WORD_COUNT = 1_000_000;
    public static final int DEFAULT_WORDS_PER_CHAPTER = 3_000;
    public static final int DEFAULT_CHAPTERS_PER_VOLUME = 20;
    public static final int DEFAULT_WORD_COUNT_TOLERANCE = 10_000;

    private StoryPacingPolicy() {
    }

    public static int resolveWordsPerChapter(NovelContext context) {
        Integer wordsPerChapter = context != null ? context.getAttribute("wordsPerChapter") : null;
        return wordsPerChapter != null && wordsPerChapter > 0 ? wordsPerChapter : DEFAULT_WORDS_PER_CHAPTER;
    }

    public static int resolveTargetWordCount(NovelContext context, NovelSeed seed) {
        Integer chaptersTotal = context != null ? context.getAttribute("chaptersTotal") : null;
        Integer wordsPerChapter = context != null ? context.getAttribute("wordsPerChapter") : null;
        if (chaptersTotal != null && chaptersTotal > 0 && wordsPerChapter != null && wordsPerChapter > 0) {
            return chaptersTotal * wordsPerChapter;
        }
        Integer contextTarget = context != null ? context.getAttribute("targetWordCount") : null;
        if (contextTarget != null && contextTarget > 0) {
            return contextTarget;
        }
        if (seed != null && seed.getTargetWordCount() != null && seed.getTargetWordCount() > 0) {
            return seed.getTargetWordCount();
        }
        return DEFAULT_TARGET_WORD_COUNT;
    }

    public static int resolveWordCountTolerance(NovelContext context) {
        Integer tolerance = context != null ? context.getAttribute("wordCountTolerance") : null;
        return tolerance != null && tolerance > 0 ? tolerance : DEFAULT_WORD_COUNT_TOLERANCE;
    }

    public static int resolveVolumeTargetWordCount(NovelContext context) {
        Integer volumeTarget = context != null ? context.getAttribute("volumeTargetWordCount") : null;
        if (volumeTarget != null && volumeTarget > 0) {
            return volumeTarget;
        }
        Integer totalVolumes = context != null ? context.getAttribute("totalVolumes") : null;
        int target = resolveTargetWordCount(context, null);
        if (totalVolumes != null && totalVolumes > 0) {
            return Math.max(1, (target + totalVolumes - 1) / totalVolumes);
        }
        return 0;
    }

    public static int resolveTotalVolumes(NovelContext context, NovelSeed seed, int totalChapters) {
        Integer forced = context != null ? context.getAttribute("totalVolumes") : null;
        if (forced != null && forced > 0) {
            return forced;
        }
        int chaptersPerVolume = Math.min(DEFAULT_CHAPTERS_PER_VOLUME, Math.max(1, totalChapters));
        return Math.max(1, (totalChapters + chaptersPerVolume - 1) / chaptersPerVolume);
    }

    public static int resolveChaptersPerVolume(NovelContext context, int totalChapters, int totalVolumes) {
        if (totalVolumes <= 0) {
            return DEFAULT_CHAPTERS_PER_VOLUME;
        }
        return Math.max(1, (totalChapters + totalVolumes - 1) / totalVolumes);
    }

    public static int resolveTotalChapters(NovelContext context, NovelSeed seed) {
        Integer chaptersTotal = context != null ? context.getAttribute("chaptersTotal") : null;
        if (chaptersTotal != null && chaptersTotal > 0) {
            return chaptersTotal;
        }
        int targetWordCount = resolveTargetWordCount(context, seed);
        int wordsPerChapter = resolveWordsPerChapter(context);
        return Math.max(1, (targetWordCount + wordsPerChapter - 1) / wordsPerChapter);
    }

    public static int resolvePlannedTotalChapters(NovelPlan plan) {
        if (plan == null) {
            return 0;
        }
        if (plan.getTotalChapters() != null && plan.getTotalChapters() > 0) {
            return plan.getTotalChapters();
        }
        int totalVolumes = plan.getTotalVolumes() != null && plan.getTotalVolumes() > 0 ? plan.getTotalVolumes() : 1;
        int chaptersPerVolume = plan.getChaptersPerVolume() != null && plan.getChaptersPerVolume() > 0
                ? plan.getChaptersPerVolume()
                : DEFAULT_CHAPTERS_PER_VOLUME;
        return totalVolumes * chaptersPerVolume;
    }

    public static int resolveChapterCountForVolume(NovelPlan plan, int volumeNumber) {
        if (plan == null) {
            return DEFAULT_CHAPTERS_PER_VOLUME;
        }
        int chaptersPerVolume = plan.getChaptersPerVolume() != null && plan.getChaptersPerVolume() > 0
                ? plan.getChaptersPerVolume()
                : DEFAULT_CHAPTERS_PER_VOLUME;
        int totalChapters = resolvePlannedTotalChapters(plan);
        if (totalChapters <= 0) {
            return chaptersPerVolume;
        }
        return Math.min(chaptersPerVolume, Math.max(0, totalChapters - (Math.max(1, volumeNumber) - 1) * chaptersPerVolume));
    }

    public static int resolveGlobalChapterNumber(NovelContext context, VolumePlan volumePlan, Integer chapterInVolume) {
        NovelPlan plan = context != null ? context.getAttribute("plan") : null;
        int chaptersPerVolume = plan != null && plan.getChaptersPerVolume() != null && plan.getChaptersPerVolume() > 0
                ? plan.getChaptersPerVolume()
                : DEFAULT_CHAPTERS_PER_VOLUME;
        int volumeNumber = volumePlan != null && volumePlan.getVolumeNumber() != null && volumePlan.getVolumeNumber() > 0
                ? volumePlan.getVolumeNumber()
                : 1;
        int chapterNumber = chapterInVolume != null && chapterInVolume > 0 ? chapterInVolume : 1;
        return (volumeNumber - 1) * chaptersPerVolume + chapterNumber;
    }

    public static String resolvePhase(int globalChapterNumber, int totalChapters) {
        if (totalChapters <= 0) {
            return "UNKNOWN";
        }
        double progress = globalChapterNumber * 1.0d / totalChapters;
        if (progress >= 0.92d) {
            return "FINAL_RESOLUTION";
        }
        if (progress >= 0.82d) {
            return "CLOSING";
        }
        if (progress >= 0.65d) {
            return "CONVERGENCE";
        }
        if (progress <= 0.18d) {
            return "SETUP";
        }
        return "DEVELOPMENT";
    }

    public static String buildPacingBrief(NovelContext context, VolumePlan volumePlan, Integer chapterInVolume) {
        NovelPlan plan = context != null ? context.getAttribute("plan") : null;
        int totalChapters = resolvePlannedTotalChapters(plan);
        int globalChapter = resolveGlobalChapterNumber(context, volumePlan, chapterInVolume);
        int wordsPerChapter = resolveWordsPerChapter(context);
        Integer targetWordCount = context != null ? context.getAttribute("targetWordCount") : null;
        int remainingIncludingCurrent = totalChapters > 0 ? Math.max(0, totalChapters - globalChapter + 1) : 0;
        String phase = resolvePhase(globalChapter, totalChapters);

        StringBuilder brief = new StringBuilder();
        brief.append("Story pacing contract:\n");
        brief.append("- Current global chapter: ").append(globalChapter).append("/").append(totalChapters).append("\n");
        brief.append("- Remaining chapters including this one: ").append(remainingIncludingCurrent).append("\n");
        brief.append("- Target total words: ").append(targetWordCount != null ? targetWordCount : "unknown").append("\n");
        brief.append("- Target words for this chapter: ").append(wordsPerChapter).append("\n");
        brief.append("- Phase: ").append(phase).append("\n");
        brief.append("- Rule: keep the novel finishable inside the planned chapters and target words.\n");
        if ("SETUP".equals(phase)) {
            brief.append("- Setup is allowed, but each new hook must have a planned payoff window.\n");
        } else if ("DEVELOPMENT".equals(phase)) {
            brief.append("- Develop existing hooks; introduce only minor hooks that can be resolved within this volume.\n");
        } else if ("CONVERGENCE".equals(phase)) {
            brief.append("- Start resolving major hooks; do not introduce new long-running mysteries.\n");
        } else if ("CLOSING".equals(phase)) {
            brief.append("- Prioritize payoff, confrontation, and answer delivery; only short-lived tension is allowed.\n");
        } else if ("FINAL_RESOLUTION".equals(phase)) {
            brief.append("- Close the main conflict, character arcs, relationships, and all remaining hooks.\n");
        }
        return brief.toString();
    }
}
