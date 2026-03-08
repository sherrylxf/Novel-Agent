package cn.bugstack.novel.config;

import cn.bugstack.novel.domain.model.valobj.Character;
import cn.bugstack.novel.domain.service.kg.IKnowledgeGraphService;
import cn.bugstack.novel.domain.service.kg.KGGraphDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 当 Neo4j 未配置时，提供占位 KG 服务，确保 KnowledgeGraphController 等仍可工作（返回空图）。
 */
@Slf4j
@Configuration
public class KgFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(IKnowledgeGraphService.class)
    public IKnowledgeGraphService placeholderKgService() {
        log.warn("KG服务未配置（Neo4j 未启用），使用占位实现，知识图谱接口将返回空数据");
        return new IKnowledgeGraphService() {
            @Override
            public KGGraphDTO getGraph(String novelId) {
                return KGGraphDTO.builder().nodes(new ArrayList<>()).edges(new ArrayList<>()).build();
            }

            @Override
            public List<Map<String, Object>> listCharacters(String novelId) {
                return new ArrayList<>();
            }

            @Override
            public List<Map<String, Object>> listForeshadowing(String novelId) {
                return new ArrayList<>();
            }

            @Override
            public void deleteCharacter(String characterId) {}

            @Override
            public void deleteForeshadowing(String foreshadowingId) {}

            @Override
            public void updateCharacter(Character character) {}

            @Override
            public void updateForeshadowing(String foreshadowingId, String content, Map<String, Object> properties) {}

            @Override
            public void createCharacter(Character character) {}

            @Override
            public void createRelationship(String fromId, String toId, String relationType, Map<String, Object> properties) {}

            @Override
            public List<Map<String, Object>> queryRelationships(String characterId, String relationType) {
                return new ArrayList<>();
            }

            @Override
            public Character queryCharacter(String characterId) {
                return null;
            }

            @Override
            public void createForeshadowing(String foreshadowingId, String content, Map<String, Object> properties) {}

            @Override
            public List<String> listUnresolvedForeshadowing(String novelId) {
                return new ArrayList<>();
            }

            @Override
            public boolean validateRule(String rule) {
                return true;
            }
        };
    }
}
