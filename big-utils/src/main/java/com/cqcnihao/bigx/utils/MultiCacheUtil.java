package com.cqcnihao.bigx.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import java.util.function.Supplier;

/**
 * 多级缓存工具类
 * 公众号：Java匠艺
 */
@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class MultiCacheUtil {

    @Autowired
    private CacheManager multiLevelCacheManager;

    /**
     * 多级查询：本地→Redis→数据库
     */
    public <T> T get(String cacheName, String key, Supplier<T> dbSupplier) {
        Cache cache = multiLevelCacheManager.getCache(cacheName);
        return cache.get(key, dbSupplier::get);
    }

    /**
     * 同时写入本地+Redis
     */
    public void put(String cacheName, String key, Object value) {
        Cache cache = multiLevelCacheManager.getCache(cacheName);
        if (cache != null) cache.put(key, value);
    }

    /**
     * 同时删除本地+Redis
     */
    public void delete(String cacheName, String key) {
        Cache cache = multiLevelCacheManager.getCache(cacheName);
        if (cache != null) cache.evict(key);
    }
}