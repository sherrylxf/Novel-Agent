package cn.bugstack.novel.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j配置类
 * 注意：如果暂时不使用知识图谱功能，可以注释掉此配置类
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Configuration
@ConditionalOnProperty(name = "spring.neo4j.uri")
public class Neo4jConfig {
    
    @Bean
    public Driver neo4jDriver(
            @Value("${spring.neo4j.uri}") String uri,
            @Value("${spring.neo4j.authentication.username}") String username,
            @Value("${spring.neo4j.authentication.password}") String password) {
        return GraphDatabase.driver(uri, 
                org.neo4j.driver.AuthTokens.basic(username, password));
    }
    
}
