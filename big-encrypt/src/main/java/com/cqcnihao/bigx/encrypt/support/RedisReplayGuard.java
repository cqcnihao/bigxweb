package com.cqcnihao.bigx.encrypt.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 基于 Redis 的防重放实现（多实例 / 集群适用）
 * <p>
 * 用 {@code SET NX EX} 原子写入 nonce，写入成功即首次请求；TTL 到期后自动回收，
 * 无需额外的清理线程。Redis 故障时由自动装配回退到 {@link InMemoryReplayGuard}。
 *
 * @author bigweb
 */
@Slf4j
@RequiredArgsConstructor
public class RedisReplayGuard implements ReplayGuard {

    private static final String KEY_PREFIX = "encrypt:nonce:";

    private final StringRedisTemplate redisTemplate;

    /** nonce 存活时长，应大于 timestamp 有效期 */
    private final Duration ttl;

    @Override
    public boolean accept(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return true;
        }
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + nonce, "1", ttl));
    }
}
