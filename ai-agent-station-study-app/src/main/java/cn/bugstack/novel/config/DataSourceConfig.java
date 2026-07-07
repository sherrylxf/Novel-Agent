package cn.bugstack.novel.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 数据源配置：单一 PostgreSQL（业务表 + pgvector 向量表 + JdbcTemplate 检索）
 * Neo4j 仍由 Spring Data Neo4j 单独配置。
 */
@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    @Bean("postgresqlDataSource")
    @Primary
    public DataSource postgresqlDataSource(
            @Value("${spring.datasource.postgresql.driver-class-name}") String driverClassName,
            @Value("${spring.datasource.postgresql.url}") String url,
            @Value("${spring.datasource.postgresql.username}") String username,
            @Value("${spring.datasource.postgresql.password}") String password,
            @Value("${spring.datasource.postgresql.hikari.maximum-pool-size:15}") int maximumPoolSize,
            @Value("${spring.datasource.postgresql.hikari.minimum-idle:5}") int minimumIdle,
            @Value("${spring.datasource.postgresql.hikari.idle-timeout:30000}") long idleTimeout,
            @Value("${spring.datasource.postgresql.hikari.connection-timeout:30000}") long connectionTimeout,
            @Value("${spring.datasource.postgresql.hikari.max-lifetime:1800000}") long maxLifetime) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setPoolName("NovelPostgreSQLHikariPool");

        return dataSource;
    }

    @Bean("sqlSessionFactory")
    public SqlSessionFactoryBean sqlSessionFactory(@Qualifier("postgresqlDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(dataSource);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sqlSessionFactoryBean.setConfigLocation(resolver.getResource("classpath:/mybatis/config/mybatis-config.xml"));
        sqlSessionFactoryBean.setMapperLocations(resolver.getResources("classpath:/mybatis/mapper/**/*.xml"));

        return sqlSessionFactoryBean;
    }

    @Bean("sqlSessionTemplate")
    public SqlSessionTemplate sqlSessionTemplate(
            @Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean("transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("postgresqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 与 MyBatis 共用同一 PostgreSQL 连接池（向量 SQL / PgVectorStore）
     */
    @Bean("pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("postgresqlDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(120);
        return jdbcTemplate;
    }
}
