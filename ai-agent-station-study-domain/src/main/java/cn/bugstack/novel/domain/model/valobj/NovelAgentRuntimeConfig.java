package cn.bugstack.novel.domain.model.valobj;

import cn.bugstack.novel.domain.model.entity.NovelAgentConfigItem;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 从 novel_agent_config 解析后的运行时视图：按 Agent 类型（与编排注册名一致，如 SceneGenerationAgent）分组的键值。
 * <p>
 * 合并规则：列表中后出现的项覆盖先出现的同 agentType + configKey（queryConfigs 应先全局后本书）。
 */
@Getter
public final class NovelAgentRuntimeConfig {

    private final Map<String, Map<String, String>> byAgentType;

    private NovelAgentRuntimeConfig(Map<String, Map<String, String>> byAgentType) {
        this.byAgentType = byAgentType;
    }

    public static NovelAgentRuntimeConfig empty() {
        return new NovelAgentRuntimeConfig(Map.of());
    }

    /**
     * @param items 已按「全局在前、本书在后」等方法排好序的列表
     */
    public static NovelAgentRuntimeConfig fromOrderedItems(List<NovelAgentConfigItem> items) {
        if (items == null || items.isEmpty()) {
            return empty();
        }
        Map<String, Map<String, String>> acc = new LinkedHashMap<>();
        for (NovelAgentConfigItem it : items) {
            if (it == null) {
                continue;
            }
            if (it.getStatus() != null && it.getStatus() != 1) {
                continue;
            }
            String at = it.getAgentType();
            String key = it.getConfigKey();
            if (at == null || key == null) {
                continue;
            }
            at = at.trim();
            key = key.trim();
            if (at.isEmpty() || key.isEmpty()) {
                continue;
            }
            acc.computeIfAbsent(at, k -> new LinkedHashMap<>()).put(key, it.getConfigValue());
        }
        Map<String, Map<String, String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : acc.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return new NovelAgentRuntimeConfig(Collections.unmodifiableMap(frozen));
    }

    /**
     * 读取某 Agent 的配置项；key 大小写不敏感（内部按小写查找，存储保留首次出现的 key 写法）。
     */
    public String get(String agentTypeCode, String configKey) {
        if (agentTypeCode == null || configKey == null) {
            return null;
        }
        Map<String, String> m = byAgentType.get(agentTypeCode.trim());
        if (m == null || m.isEmpty()) {
            return null;
        }
        String k = configKey.trim();
        if (m.containsKey(k)) {
            return m.get(k);
        }
        String lower = k.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    public Map<String, String> paramsForAgent(String agentTypeCode) {
        if (agentTypeCode == null) {
            return Map.of();
        }
        Map<String, String> m = byAgentType.get(agentTypeCode.trim());
        return m != null ? m : Map.of();
    }

    public int agentTypeCount() {
        return byAgentType.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NovelAgentRuntimeConfig that)) {
            return false;
        }
        return Objects.equals(byAgentType, that.byAgentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byAgentType);
    }
}
