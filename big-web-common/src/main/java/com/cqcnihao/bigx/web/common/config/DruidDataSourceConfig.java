package com.cqcnihao.bigx.web.common.config;

import com.alibaba.druid.pool.DruidDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
@ConditionalOnClass(DataSourceProperties.class)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
// 必须先于 Boot 的 DataSourceAutoConfiguration 注册，
// 否则 Boot 的 Generic 实现先占位，本 Bean 再注册时因不允许覆盖而启动失败（模块间类路径不同会导致顺序漂移）
@AutoConfigureBefore(name = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class DruidDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DruidDataSourceConfig.class);

    private final Environment environment;

    public DruidDataSourceConfig(Environment environment) {
        this.environment = environment;
    }

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

    /**
     * Druid 监控台启用时的弱口令守卫：
     * prod profile 使用默认 admin/admin123 直接拒绝启动；其他环境打印错误日志强提醒。
     */
    @PostConstruct
    void druidStatViewSecurityCheck() {
        boolean enabled = Boolean.parseBoolean(environment.getProperty(
                "spring.datasource.druid.stat-view-servlet.enabled", "false"));
        if (!enabled) {
            return;
        }
        String username = environment.getProperty(
                "spring.datasource.druid.stat-view-servlet.login-username", "");
        String password = environment.getProperty(
                "spring.datasource.druid.stat-view-servlet.login-password", "");
        boolean weakCredential = "admin".equals(username) || "admin123".equals(password);
        if (!weakCredential) {
            return;
        }
        boolean prod = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
        if (prod) {
            throw new IllegalStateException("[SECURITY] 生产环境启用 Druid 监控台必须修改默认账号口令"
                    + "（环境变量 DRUID_USERNAME / DRUID_PASSWORD），拒绝启动");
        }
        log.error("[SECURITY] Druid 监控台已启用且使用默认账号口令（admin/admin123），仅限本地开发使用；"
                + "生产环境必须通过环境变量 DRUID_USERNAME / DRUID_PASSWORD 覆盖");
    }
}