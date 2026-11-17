package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.support.ClientKeyStore;
import com.cqcnihao.bigx.encrypt.support.InMemoryReplayGuard;
import com.cqcnihao.bigx.encrypt.support.RedisClientKeyStore;
import com.cqcnihao.bigx.encrypt.support.RedisReplayGuard;
import com.cqcnihao.bigx.encrypt.support.ReplayGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * big-Encrypt 的 Redis 防重放自动装配
 * <p>
 * 独立成顶层自动配置类（而不是 {@link BigEncryptAutoConfiguration} 的内部类），
 * 这样 {@code @ConditionalOnProperty} 总开关对 Redis Bean 同样生效：
 * 内部类不会继承外部类的条件，模块关闭时仍会被注册，导致依赖缺失启动失败。
 * <p>
 * 仅在宿主应用引入 {@code spring-data-redis} 时生效；Redis 启动期不可用时
 * 回退 JVM 内存实现并告警，避免缓存故障拖垮业务。
 *
 * @author bigweb
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "big.encrypt", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EncryptProperties.class)
public class BigEncryptRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReplayGuard redisReplayGuard(EncryptProperties properties, StringRedisTemplate redisTemplate) {
        long ttlMillis = Math.max(properties.getTimestampValidity() * 2, 60_000L);
        RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
        if (factory != null) {
            RedisConnection connection = factory.getConnection();
            try {
                connection.ping();
            } catch (Exception e) {
                log.warn("Redis 不可用，防重放回退为 JVM 内存版（仅单实例有效）：{}", e.getMessage());
                return new InMemoryReplayGuard(ttlMillis);
            } finally {
                connection.close();
            }
        }
        log.info("防重放已启用 Redis 实现，nonce TTL={}ms", ttlMillis);
        return new RedisReplayGuard(redisTemplate, Duration.ofMillis(ttlMillis));
    }

    /**
     * 客户端 SM2 公钥存储：必须走 Redis。
     * <p>
     * 客户端公钥在 A 实例登记、请求被负载均衡打到 B 实例时，
     * 内存实现会让 B 实例查不到公钥而拒绝<b>合法</b>请求，现象是「随机性验签失败」，
     * 排查成本极高。因此只要宿主引入 Redis，就强制使用共享存储。
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientKeyStore redisClientKeyStore(StringRedisTemplate redisTemplate) {
        log.info("客户端 SM2 公钥存储已启用 Redis 实现");
        return new RedisClientKeyStore(redisTemplate);
    }
}