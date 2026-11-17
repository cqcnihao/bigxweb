package com.cqcnihao.bigx.auth.captcha;

import com.anji.captcha.service.CaptchaCacheService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * AJ-Captcha 的 Redis 缓存实现（验证码 get/check/verify 状态存 Redis，支持多实例部署）
 * <p>
 * AJ-Captcha 通过 <b>Java SPI</b>（{@code META-INF/services/com.anji.captcha.service.CaptchaCacheService}）
 * 实例化缓存实现，因此本类必须是<b>无参构造</b>；
 * RedisTemplate 由 {@code AjCaptchaConfig} 在装配 CaptchaService 前通过 {@link #init(StringRedisTemplate)} 注入。
 * <p>
 * Redis 键均带 {@code aj-captcha:} 前缀，与项目其他 Redis 数据隔离。
 *
 * @author bigweb
 */
public class CaptchaCacheServiceRedisImpl implements CaptchaCacheService {

    private static final String KEY_PREFIX = "aj-captcha:";

    private static volatile StringRedisTemplate redisTemplate;

    /** SPI 无参构造后由配置类注入模板（在 CaptchaServiceFactory.getInstance 之前调用） */
    public static void init(StringRedisTemplate template) {
        redisTemplate = template;
    }

    private static StringRedisTemplate template() {
        StringRedisTemplate t = redisTemplate;
        if (t == null) {
            throw new IllegalStateException("CaptchaCacheServiceRedisImpl 尚未初始化：请确认 AjCaptchaConfig 在创建 CaptchaService 前调用了 init()");
        }
        return t;
    }

    @Override
    public void set(String key, String value, long expiresInSeconds) {
        template().opsForValue().set(KEY_PREFIX + key, value,
                Duration.ofSeconds(Math.max(expiresInSeconds, 1L)));
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(template().hasKey(KEY_PREFIX + key));
    }

    @Override
    public void delete(String key) {
        template().delete(KEY_PREFIX + key);
    }

    @Override
    public String get(String key) {
        return template().opsForValue().get(KEY_PREFIX + key);
    }

    @Override
    public Long increment(String key, long val) {
        Long count = template().opsForValue().increment(KEY_PREFIX + key, val);
        return count == null ? 0L : count;
    }

    @Override
    public void setExpire(String key, long expiresInSeconds) {
        template().expire(KEY_PREFIX + key, Duration.ofSeconds(Math.max(expiresInSeconds, 1L)));
    }

    @Override
    public String type() {
        return "redis";
    }
}
