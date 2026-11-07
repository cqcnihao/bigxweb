package com.cqcnihao.bigx.web.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(DataSource.class)
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public Health health() {
        long startTime = System.currentTimeMillis();
        
        try (Connection conn = dataSource.getConnection()) {
            // 1. 检查连接有效性
            if (!conn.isValid(3)) {
                log.error("数据库连接无效");
                return Health.down()
                    .withDetail("error", "数据库连接无效")
                    .build();
            }
            
            // 2. 执行简单查询验证
            try (ResultSet rs = conn.prepareStatement("SELECT 1").executeQuery()) {
                if (!rs.next()) {
                    return Health.down()
                        .withDetail("error", "数据库查询失败")
                        .build();
                }
            }
            
            // 3. 获取数据库信息
            String databaseProductName = conn.getMetaData().getDatabaseProductName();
            String databaseProductVersion = conn.getMetaData().getDatabaseProductVersion();
            String url = conn.getMetaData().getURL();
            
            // 4. 获取连接池信息（如果是Druid）
            int activeCount = getActiveConnectionCount();
            int maxActive = getMaxActiveConnections();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            log.debug("数据库健康检查通过: database={}, responseTime={}ms, active={}, max={}", 
                databaseProductName, responseTime, activeCount, maxActive);
            
            // 5. 根据响应时间判断健康状态
            if (responseTime > 1000) {
                return Health.outOfService()
                    .withDetail("database", databaseProductName)
                    .withDetail("version", databaseProductVersion)
                    .withDetail("url", url)
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("warning", "数据库响应时间过长")
                    .build();
            }
            
            return Health.up()
                .withDetail("database", databaseProductName)
                .withDetail("version", databaseProductVersion)
                .withDetail("url", url)
                .withDetail("responseTime", responseTime + "ms")
                .withDetail("activeConnections", activeCount)
                .withDetail("maxConnections", maxActive)
                .build();
                
        } catch (SQLException e) {
            log.error("数据库健康检查失败: {}", e.getMessage(), e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("sqlState", e.getSQLState())
                .withDetail("errorCode", e.getErrorCode())
                .build();
        }
    }
    
    /**
     * 获取当前活跃连接数
     */
    private int getActiveConnectionCount() {
        try {
            // 尝试从Druid数据源获取
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.processlist WHERE command != 'Sleep'",
                Integer.class
            );
            return count != null ? count : -1;
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * 获取最大连接数
     */
    private int getMaxActiveConnections() {
        try {
            // 尝试从Druid数据源获取
            Integer max = jdbcTemplate.queryForObject(
                "SHOW VARIABLES LIKE 'max_connections'",
                Integer.class
            );
            return max != null ? max : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}