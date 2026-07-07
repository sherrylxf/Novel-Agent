package cn.bugstack.novel.domain.agent.pipeline;

import cn.bugstack.novel.types.enums.GenerationStage;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 生成阶段状态机：定义业务阶段枚举及<strong>典型下一跳</strong>（用于校验、监控与文档化）。
 * <p>
 * 运行时仍以各节点 {@code doExecute} 返回值为准；非常规迁移仅打日志告警，不强制拦截（多卷多章动态分支）。
 */
@Slf4j
public final class GenerationStageStateMachine {

    private static final Map<GenerationStage, Set<GenerationStage>> TYPICAL_NEXT = buildTypicalNext();

    private GenerationStageStateMachine() {
    }

    private static Map<GenerationStage, Set<GenerationStage>> buildTypicalNext() {
        Map<GenerationStage, Set<GenerationStage>> m = new EnumMap<>(GenerationStage.class);
        m.put(GenerationStage.SEED, Set.of(GenerationStage.NOVEL_PLAN));
        m.put(GenerationStage.NOVEL_PLAN, Set.of(GenerationStage.VOLUME_PLAN));
        m.put(GenerationStage.VOLUME_PLAN, Set.of(GenerationStage.CHAPTER_OUTLINE));
        m.put(GenerationStage.CHAPTER_OUTLINE, Set.of(GenerationStage.SCENE_GENERATION));
        m.put(GenerationStage.SCENE_GENERATION, Set.of(GenerationStage.VALIDATION));
        m.put(GenerationStage.VALIDATION, Set.of(GenerationStage.CHAPTER_OUTLINE, GenerationStage.VOLUME_PLAN, GenerationStage.COMPLETE));
        m.put(GenerationStage.COMPLETE, Collections.emptySet());
        return Collections.unmodifiableMap(m);
    }

    /**
     * 典型下一阶段集合（一卷多章时 VALIDATION 之后常回到 CHAPTER_OUTLINE）。
     */
    public static Set<GenerationStage> typicalNextStages(GenerationStage current) {
        if (current == null) {
            return Set.of();
        }
        return TYPICAL_NEXT.getOrDefault(current, Collections.emptySet());
    }

    /**
     * 解析阶段字符串（支持枚举常量名如 SEED，或中文展示名如「种子阶段」）。
     * ROOT/LOAD_DATA 等非 {@link GenerationStage} 值返回 empty。
     */
    public static Optional<GenerationStage> parseStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(GenerationStage.valueOf(stage.trim()));
        } catch (IllegalArgumentException ignored) {
            for (GenerationStage s : GenerationStage.values()) {
                if (stage.equals(s.getName())) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * 是否为状态机表中登记的典型迁移（双向有一端无法解析时视为 true，避免误报）。
     */
    public static boolean isTypicalTransition(GenerationStage from, GenerationStage to) {
        if (from == null || to == null) {
            return true;
        }
        return typicalNextStages(from).contains(to);
    }

    /**
     * 若从当前阶段到下一阶段不在典型集合内，打 WARN（动态分支可忽略）。
     */
    public static void logIfAtypicalTransition(String fromStage, String toStage) {
        Optional<GenerationStage> from = parseStage(fromStage);
        Optional<GenerationStage> to = parseStage(toStage);
        if (from.isEmpty() || to.isEmpty()) {
            return;
        }
        if (!isTypicalTransition(from.get(), to.get())) {
            log.warn("阶段迁移非常规路径: {} -> {}（若为 EndingAgent 动态分支可忽略）", fromStage, toStage);
        }
    }
}
