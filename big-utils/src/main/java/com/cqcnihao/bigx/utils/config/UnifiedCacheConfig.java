package com.cqcnihao.bigx.utils.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 统一缓存配置：支持多级缓存（Caffeine本地 + Redis分布式）
 * 
 * 功能特性：
 * 1. RedisTemplate配置：String序列化Key，JSON序列化Value
 * 2. RedisCacheManager：支持@Cacheable等注解
 * 3. MultiLevelCacheManager：多级缓存，优先查本地→Redis→数据库
 * 
 * @author bigweb
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@EnableCaching
public class UnifiedCacheConfig {

    /**
     * RedisTemplate 配置
     * 使用 String 序列化 Key，JSON 序列化 Value
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 StringRedisSerializer 序列化 Key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用 GenericJackson2JsonRedisSerializer 序列化 Value
        GenericJacksonJsonRedisSerializer jsonSerializer = createJsonSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis缓存管理器配置
     * 支持 @Cacheable、@CachePut、@CacheEvict 等注解
     * 支持针对不同缓存名称的个性化配置
     */
    @Bean
    @Primary
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))  // 默认 30 分钟过期
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(createJsonSerializer()))
                .disableCachingNullValues()  // 不缓存 null 值
                .prefixCacheNameWith("cache:");  // key 前缀

        // 针对不同缓存名的个性化配置
        RedisCacheConfiguration userCacheConfig = defaultConfig.entryTtl(Duration.ofHours(2));  // 用户缓存2小时
        RedisCacheConfiguration permissionConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));  // 权限缓存10分钟
        RedisCacheConfiguration menuConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));  // 菜单缓存10分钟
        RedisCacheConfiguration dictConfig = defaultConfig.entryTtl(Duration.ofHours(1));  // 字典缓存1小时
        RedisCacheConfiguration configCacheConfig = defaultConfig.entryTtl(Duration.ofMinutes(30));  // 配置缓存30分钟
        RedisCacheConfiguration tokenCacheConfig = defaultConfig.entryTtl(Duration.ofHours(2));  // Token缓存2小时
        RedisCacheConfiguration productCacheConfig = defaultConfig.entryTtl(Duration.ofMinutes(30));  // 商品缓存30分钟

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("userCache", userCacheConfig)
                .withCacheConfiguration("user", userCacheConfig)
                .withCacheConfiguration("permission", permissionConfig)
                .withCacheConfiguration("menu", menuConfig)
                .withCacheConfiguration("dict", dictConfig)
                .withCacheConfiguration("config", configCacheConfig)
                .withCacheConfiguration("configCache", configCacheConfig)
                .withCacheConfiguration("tokenCache", tokenCacheConfig)
                .withCacheConfiguration("productCache", productCacheConfig)
                .transactionAware()
                .build();
    }

    /**
     * Caffeine本地缓存配置
     * 用于热点数据缓存
     */
    @Bean
    public Caffeine<Object, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000) // 最大容量，防OOM
                .expireAfterWrite(Duration.ofMinutes(5)) // 本地5分钟过期
                .initialCapacity(1000)
                .recordStats() // 开启统计
                .removalListener((key, value, cause) -> {
                    if (cause == RemovalCause.EXPIRED) {
                        log.debug("本地缓存过期: key={}", key);
                    }
                });
    }

    /**
     * Caffeine本地缓存管理器
     * 用于独立的本地缓存场景
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager(Caffeine<Object, Object> caffeine) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<CaffeineCache> caches = new ArrayList<>();
        
        // 1. 用户缓存（热点数据）
        caches.add(buildCache("user", 1000, 5));
        
        // 2. 权限缓存
        caches.add(buildCache("permission", 500, 10));
        
        // 3. 菜单缓存
        caches.add(buildCache("menu", 200, 10));
        
        // 4. 字典缓存
        caches.add(buildCache("dict", 1000, 60));
        
        // 5. 配置缓存
        caches.add(buildCache("config", 500, 30));
        
        cacheManager.setCaches(caches);
        
        log.info("Caffeine本地缓存初始化完成，共{}个缓存", caches.size());
        return cacheManager;
    }

    /**
     * 多级缓存管理器（可选）
     * 优先查Caffeine → 未命中查Redis → 未命中查库
     * 
     * 使用方式：在需要多级缓存的场景中注入此CacheManager
     * 默认使用redisCacheManager（@Primary标注）
     */
    @Bean("multiLevelCacheManager")
    public CacheManager multiLevelCacheManager(Caffeine<Object, Object> caffeine,
                                               RedisCacheManager redisCacheManager) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<Cache> caches = new ArrayList<>();
        String[] cacheNames = {"productCache", "userCache", "configCache", "user", "permission", "menu", "dict", "config"};

        for (String name : cacheNames) {
            CaffeineCache localCache = new CaffeineCache(name, caffeine.build());
            Cache multiCache = createMultiLevelCache(name, localCache, redisCacheManager);
            caches.add(multiCache);
        }
        
        cacheManager.setCaches(caches);
        log.info("多级缓存管理器初始化完成，缓存名称: {}", cacheNames);
        return cacheManager;
    }

    /**
     * 构建Caffeine缓存
     * 
     * @param name 缓存名称
     * @param maxSize 最大容量
     * @param expireMinutes 过期时间（分钟）
     * @return Caffeine缓存
     */
    private CaffeineCache buildCache(String name, int maxSize, int expireMinutes) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                .recordStats()
                .removalListener((Object key, Object value, RemovalCause cause) -> {
                    if (cause == RemovalCause.EXPIRED) {
                        log.debug("缓存过期: cache={}, key={}", name, key);
                    }
                })
                .build());
    }

    /**
     * 创建多级缓存实例
     */
    private Cache createMultiLevelCache(String name, CaffeineCache localCache, 
                                       RedisCacheManager redisCacheManager) {
        return new Cache() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Object getNativeCache() {
                return localCache.getNativeCache();
            }

            // 优先查本地
            @Override
            public ValueWrapper get(Object key) {
                ValueWrapper local = localCache.get(key);
                if (local != null) return local;

                // 本地未命中，查Redis
                Cache redis = redisCacheManager.getCache(name);
                if (redis == null) return null;
                
                ValueWrapper redisVal = redis.get(key);
                if (redisVal != null) {
                    localCache.put(key, redisVal.get()); // 回写本地
                    return redisVal;
                }
                return null;
            }

            @Override
            public <T> T get(Object key, Class<T> type) {
                ValueWrapper wrapper = get(key);
                return wrapper != null ? type.cast(wrapper.get()) : null;
            }

            // 查库+回写双缓存
            @Override
            public <T> T get(Object key, Callable<T> callable) {
                ValueWrapper val = get(key);
                if (val != null) return (T) val.get();
                try {
                    T data = callable.call();
                    if (data != null) put(key, data);
                    return data;
                } catch (Exception e) {
                    throw new RuntimeException("查询数据库失败", e);
                }
            }

            // 同时写本地+Redis
            @Override
            public void put(Object key, Object value) {
                localCache.put(key, value);
                Cache redis = redisCacheManager.getCache(name);
                if (redis != null) {
                    redis.put(key, value);
                }
            }

            // 同时删本地+Redis
            @Override
            public void evict(Object key) {
                localCache.evict(key);
                Cache redis = redisCacheManager.getCache(name);
                if (redis != null) {
                    redis.evict(key);
                }
            }

            @Override
            public void clear() {
                localCache.clear();
                Cache redis = redisCacheManager.getCache(name);
                if (redis != null) {
                    redis.clear();
                }
            }
        };
    }

    /**
     * 创建 JSON 序列化器
     * 支持复杂对象的序列化/反序列化
     */
    private GenericJacksonJsonRedisSerializer createJsonSerializer() {
        tools.jackson.databind.ObjectMapper objectMapper = 
            new tools.jackson.databind.ObjectMapper()
                .rebuild()
                .changeDefaultVisibility(vc -> vc.withVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY))
                .activateDefaultTyping(
                    tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                        .allowIfSubTypeIsArray()
                        .build(),
                    tools.jackson.databind.DefaultTyping.NON_FINAL
                )
                .build();
        return new GenericJacksonJsonRedisSerializer(objectMapper);
    }
}