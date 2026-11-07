package com.cqcnihao.bigx.web.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisHealthIndicator implements HealthIndicator {
    
    private final RedisConnectionFactory redisConnectionFactory;
    
    @Override
    public Health health() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 执行PING命令
            String result = redisConnectionFactory.getConnection().ping();
            
            if (!"PONG".equalsIgnoreCase(result)) {
                log.error("Redis PING响应异常: {}", result);
                return Health.down()
                    .withDetail("error", "Redis响应异常: " + result)
                    .build();
            }
            
            // 2. 获取Redis信息
            var connection = redisConnectionFactory.getConnection();
            var info = connection.info();
            
            String redisVersion = info.getProperty("redis_version", "unknown");
            String os = info.getProperty("os", "unknown");
            String usedMemory = info.getProperty("used_memory_human", "unknown");
            String connectedClients = info.getProperty("connected_clients", "unknown");
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            log.debug("Redis健康检查通过: version={}, responseTime={}ms, memory={}, clients={}", 
                redisVersion, responseTime, usedMemory, connectedClients);
            
            // 3. 根据响应时间判断健康状态
            if (responseTime > 500) {
                return Health.outOfService()
                    .withDetail("version", redisVersion)
                    .withDetail("os", os)
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("usedMemory", usedMemory)
                    .withDetail("connectedClients", connectedClients)
                    .withDetail("warning", "Redis响应时间过长")
                    .build();
            }
            
            return Health.up()
                .withDetail("version", redisVersion)
                .withDetail("os", os)
                .withDetail("responseTime", responseTime + "ms")
                .withDetail("usedMemory", usedMemory)
                .withDetail("connectedClients", connectedClients)
                .build();
                
        } catch (Exception e) {
            log.error("Redis健康检查失败: {}", e.getMessage(), e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("type", e.getClass().getSimpleName())
                .build();
        }
    }
}