package com.cqcnihao.bigx.encrypt.support;

/**
 * 防重放守卫
 * <p>
 * 默认实现是 JVM 内存的 {@link InMemoryReplayGuard}，只适用于单实例部署。
 * 多实例 / 网关场景请基于 Redis 实现本接口并注册为 Bean：
 * <pre>
 * &#64;Bean
 * &#64;ConditionalOnMissingBean(ReplayGuard.class)
 * public ReplayGuard replayGuard(StringRedisTemplate redis) {
 *     return nonce -> Boolean.TRUE.equals(
 *             redis.opsForValue().setIfAbsent("encrypt:nonce:" + nonce, "1", Duration.ofMinutes(10)));
 * }
 * </pre>
 *
 * @author bigweb
 */
public interface ReplayGuard {

    /**
     * 登记 nonce，返回是否首次出现
     *
     * @param nonce 随机串
     * @return true = 首次请求，放行；false = 重放，拒绝
     */
    boolean accept(String nonce);
}
