package com.cqcnihao.bigx.encrypt.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 基于 Redis 的客户端公钥存储（多实例 / 集群适用）
 * <p>
 * 客户端公钥必须在所有服务实例间共享：否则请求被负载均衡打到未登记公钥的实例时，
 * 会以 {@code CODE_SIGN_INVALID} 拒绝合法请求，且现象是「间歇性失败」，
 * 极难定位。因此只要宿主应用引入了 Redis 就优先使用本实现。
 *
 * @author bigweb
 */
@Slf4j
@RequiredArgsConstructor
public class RedisClientKeyStore implements ClientKeyStore {

    private static final String KEY_PREFIX = "encrypt:client-key:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String clientKeyId, String publicKey, long ttlSeconds) {
        if (clientKeyId == null || clientKeyId.isBlank() || publicKey == null) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + clientKeyId, publicKey,
                Duration.ofSeconds(Math.max(ttlSeconds, 60L)));
    }

    @Override
    public String find(String clientKeyId) {
        if (clientKeyId == null || clientKeyId.isBlank()) {
            return null;
        }
        return redisTemplate.opsForValue().get(KEY_PREFIX + clientKeyId);
    }

    @Override
    public void remove(String clientKeyId) {
        if (clientKeyId != null) {
            redisTemplate.delete(KEY_PREFIX + clientKeyId);
        }
    }
}