package com.kfd.cloudgallery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据源配置
 */
// 完全禁用PostgreSQL配置类，使用动态连接创建
// @Configuration
// @ConditionalOnProperty(name = "postgres.enabled", havingValue = "true", matchIfMissing = false)
public class PostgresConfig {

    @Value("${postgres.url:jdbc:postgresql://localhost:5433/sensor_data_system}")
    private String postgresUrl;

    @Value("${postgres.username:postgres}")
    private String postgresUsername;

    @Value("${postgres.password:postgres}")
    private String postgresPassword;

    @Bean(name = "postgresDataSource")
    @Lazy  // 懒加载：只在需要时创建PostgreSQL连接
    public DataSource postgresDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgresUrl);
        dataSource.setUsername(postgresUsername);
        dataSource.setPassword(postgresPassword);
        // 设置连接池属性，避免与主数据源冲突
        java.util.Properties props = new java.util.Properties();
        props.setProperty("autoCommit", "false");
        props.setProperty("readOnly", "false");
        dataSource.setConnectionProperties(props);
        return dataSource;
    }

    @Bean(name = "postgresJdbcTemplate")
    @Lazy  // 懒加载：只在需要时创建JdbcTemplate
    public JdbcTemplate postgresJdbcTemplate() {
        return new JdbcTemplate(postgresDataSource());
    }
}
