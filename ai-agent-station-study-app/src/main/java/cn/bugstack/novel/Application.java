package cn.bugstack.novel;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;

/**
 * Novel Agent 应用启动类
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@SpringBootApplication(
        scanBasePackages = {"cn.bugstack.novel"},
        exclude = {
                // 排除自动配置，使用自定义多数据源配置
                DataSourceAutoConfiguration.class,
                Neo4jAutoConfiguration.class,
                // 排除 Spring AI PgVectorStore 自动配置，使用自定义 VectorStoreConfig
                PgVectorStoreAutoConfiguration.class
        }
)
public class Application {
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
}
