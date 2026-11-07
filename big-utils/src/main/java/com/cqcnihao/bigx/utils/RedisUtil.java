package com.cqcnihao.bigx.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类
 * 提供常用的 Redis 操作方法，简化缓存使用
 * 
 * @author bigweb
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class RedisUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 通用操作 ====================

    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间（秒）
     */
    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            log.error("设置缓存过期时间失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 根据 key 获取过期时间
     *
     * @param key 键
     * @return 时间（秒），0 代表永久有效
     */
    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断 key 是否存在
     *
     * @param key 键
     * @return true 存在，false 不存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("判断缓存key是否存在失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值或多个
     */
    @SuppressWarnings("unchecked")
    public void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete((Collection<String>) List.of(key));
            }
        }
    }

    // ==================== String 操作 ====================

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true 成功，false 失败
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间（秒），如果 time < 0 则设置无限时间
     * @return true 成功，false 失败
     */
    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几（大于0）
     */
    public long incr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递增因子必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几（大于0）
     */
    public long decr(String key, long delta) {
        if (delta < 0) {
            throw new RuntimeException("递减因子必须大于0");
        }
        return redisTemplate.opsForValue().increment(key, -delta);
    }

    // ==================== Hash 操作 ====================

    /**
     * HashGet
     *
     * @param key  键（不能为 null）
     * @param item 项（不能为 null）
     */
    public Object hget(String key, String item) {
        return redisTemplate.opsForHash().get(key, item);
    }

    /**
     * 获取 hashKey 对应的所有键值
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hmget(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * HashSet
     *
     * @param key 键
     * @param map 对应多个键值
     */
    public boolean hmset(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            log.error("设置Hash缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间（秒）
     */
    public boolean hmset(String key, Map<String, Object> map, long time) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("设置Hash缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 向一张 hash 表中放入数据，如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     */
    public boolean hset(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            log.error("设置Hash缓存失败: key={}, item={}", key, item, e);
            return false;
        }
    }

    /**
     * 向一张 hash 表中放入数据，如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间（秒），注意：如果已存在的 hash 表有时间，这里将会替换原有的时间
     */
    public boolean hset(String key, String item, Object value, long time) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("设置Hash缓存失败: key={}, item={}", key, item, e);
            return false;
        }
    }

    /**
     * 删除 hash 表中的值
     *
     * @param key  键
     * @param item 项（可以使多个）
     */
    public void hdel(String key, Object... item) {
        redisTemplate.opsForHash().delete(key, item);
    }

    /**
     * 判断 hash 表中是否有该项的值
     *
     * @param key  键
     * @param item 项
     */
    public boolean hHasKey(String key, String item) {
        return redisTemplate.opsForHash().hasKey(key, item);
    }

    // ==================== Set 操作 ====================

    /**
     * 根据 key 获取 Set 中的所有值
     *
     * @param key 键
     */
    public Set<Object> sGet(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("获取Set缓存失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 根据 value 从一个 set 中查询，是否存在
     *
     * @param key   键
     * @param value 值
     */
    public boolean sHasKey(String key, Object value) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            log.error("查询Set缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 将数据放入 set 缓存
     *
     * @param key    键
     * @param values 值（可以是多个）
     */
    public long sSet(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            log.error("设置Set缓存失败: key={}", key, e);
            return 0;
        }
    }

    /**
     * 将 set 数据放入缓存
     *
     * @param key    键
     * @param time   时间（秒）
     * @param values 值（可以是多个）
     */
    public long sSetAndTime(String key, long time, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                expire(key, time);
            }
            return count;
        } catch (Exception e) {
            log.error("设置Set缓存失败: key={}", key, e);
            return 0;
        }
    }

    /**
     * 获取 set 缓存的长度
     *
     * @param key 键
     */
    public long sGetSetSize(String key) {
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            log.error("获取Set缓存长度失败: key={}", key, e);
            return 0;
        }
    }

    /**
     * 移除 set 缓存中的值
     *
     * @param key    键
     * @param values 值（可以是多个）
     */
    public long setRemove(String key, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().remove(key, values);
            return count;
        } catch (Exception e) {
            log.error("移除Set缓存失败: key={}", key, e);
            return 0;
        }
    }

    // ==================== List 操作 ====================

    /**
     * 获取 list 缓存的内容
     *
     * @param key   键
     * @param start 开始
     * @param end   结束（0 到 -1 代表所有值）
     */
    public List<Object> lGet(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            log.error("获取List缓存失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 获取 list 缓存的长度
     *
     * @param key 键
     */
    public long lGetListSize(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            log.error("获取List缓存长度失败: key={}", key, e);
            return 0;
        }
    }

    /**
     * 通过索引获取 list 中的值
     *
     * @param key   键
     * @param index 索引（index >= 0 时，0 表头，1 第二个元素；index < 0 时，-1 表尾，-2 倒数第二个元素）
     */
    public Object lGetIndex(String key, long index) {
        try {
            return redisTemplate.opsForList().index(key, index);
        } catch (Exception e) {
            log.error("获取List索引值失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 将 list 放入缓存
     *
     * @param key   键
     * @param value 值
     */
    public boolean lSet(String key, Object value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            log.error("设置List缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 将 list 放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间（秒）
     */
    public boolean lSet(String key, Object value, long time) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("设置List缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 将 list 放入缓存（批量）
     *
     * @param key   键
     * @param value 值
     */
    public boolean lSetList(String key, List<Object> value) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            log.error("设置List缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 将 list 放入缓存（批量）
     *
     * @param key   键
     * @param value 值
     * @param time  时间（秒）
     */
    public boolean lSetList(String key, List<Object> value, long time) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            log.error("设置List缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 根据索引修改 list 中的某条数据
     *
     * @param key   键
     * @param index 索引
     * @param value 值
     */
    public boolean lUpdateIndex(String key, long index, Object value) {
        try {
            redisTemplate.opsForList().set(key, index, value);
            return true;
        } catch (Exception e) {
            log.error("更新List索引值失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 移除 N 个值为 value 的数据
     *
     * @param key   键
     * @param count 移除个数
     * @param value 值
     */
    public long lRemove(String key, long count, Object value) {
        try {
            return redisTemplate.opsForList().remove(key, count, value);
        } catch (Exception e) {
            log.error("移除List缓存失败: key={}", key, e);
            return 0;
        }
    }
}