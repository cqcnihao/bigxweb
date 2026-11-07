package com.cqcnihao.bigx.common.auth;

import cn.dev33.satoken.dao.auto.SaTokenDaoByObjectFollowString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token 持久层：Redis String 存储。
 * 对象/会话的 JSON 序列化由 SaSerializerTemplate（见 Jackson3SaSerializerTemplate）完成，
 * Object 与 Session 系列方法由 SaTokenDaoByObjectFollowString 基于 String 方法默认派生，
 * 键的前缀拼接由 Sa-Token 框架完成，本类不做二次加工。
 */
@Component
@Primary
@ConditionalOnClass(StringRedisTemplate.class)
public class JacksonSaTokenDao implements SaTokenDaoByObjectFollowString {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            stringRedisTemplate.opsForValue().set(key, value);
        } else if (timeout <= 0) {
            // timeout=0 语义为立即过期，等价于删除
            stringRedisTemplate.delete(key);
        } else {
            stringRedisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
        }
    }

    @Override
    public void update(String key, String value) {
        // update 语义要求保留原 TTL：先取值再恢复剩余过期时间（SET 会清除 TTL）
        long ttl = getTimeout(key);
        stringRedisTemplate.opsForValue().set(key, value);
        if (ttl > 0) {
            stringRedisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        }
    }

    @Override
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public long getTimeout(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl == NOT_VALUE_EXPIRE) {
            return NOT_VALUE_EXPIRE;
        }
        // Redis 的 -1（无过期时间）与 Sa-Token 的 NEVER_EXPIRE 同值，其余正数即为剩余秒数
        return ttl;
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            stringRedisTemplate.persist(key);
        } else if (timeout <= 0) {
            stringRedisTemplate.delete(key);
        } else {
            stringRedisTemplate.expire(key, timeout, TimeUnit.SECONDS);
        }
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        Set<String> keys = stringRedisTemplate.keys(prefix + "*" + keyword + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keyList = new ArrayList<>(keys);
        keyList.sort(sortType ? String::compareTo : Collections.reverseOrder(String::compareTo));
        int fromIndex = Math.min(Math.max(start, 0), keyList.size());
        int toIndex = size < 0 ? keyList.size() : Math.min(fromIndex + size, keyList.size());
        return keyList.subList(fromIndex, toIndex);
    }
}
