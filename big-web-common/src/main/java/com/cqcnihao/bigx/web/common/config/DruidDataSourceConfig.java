package com.cqcnihao.bigx.web.common.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@ConditionalOnClass(DataSourceProperties.class)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
// 必须先于 Boot 的 DataSourceAutoConfiguration 注册，
// 否则 Boot 的 Generic 实现先占位，本 Bean 再注册时因不允许覆盖而启动失败（模块间类路径不同会导致顺序漂移）
@AutoConfigureBefore(name = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class DruidDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.druid")
    public DataSource dataSource(DataSourceProperties properties) {
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(properties.determineUrl());
        ds.setDriverClassName(properties.determineDriverClassName());
        ds.setUsername(properties.determineUsername());
        ds.setPassword(properties.determinePassword());
        return ds;
    }
}