package com.cqcnihao.bigx.common.auth;

import cn.dev33.satoken.serializer.SaSerializerTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Sa-Token 序列化器：基于 Jackson 3（Spring Boot 4 内置的 tools.jackson）的 JSON 实现。
 * 注册为 Bean 后由 SaBeanInject 自动装配进 SaManager，
 * 供 SaTokenDaoByObjectFollowString 默认方法完成对象/会话与字符串的互转。
 */
@Component
@ConditionalOnClass(ObjectMapper.class)
public class Jackson3SaSerializerTemplate implements SaSerializerTemplate {

    private final ObjectMapper objectMapper;

    public Jackson3SaSerializerTemplate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String objectToString(Object obj) {
        if (obj == null) {
            return null;
        }
        return objectMapper.writeValueAsString(obj);
    }

    @Override
    public Object stringToObject(String str) {
        if (str == null) {
            return null;
        }
        return objectMapper.readValue(str, Object.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T stringToObject(String str, Class<T> type) {
        if (str == null) {
            return null;
        }
        return objectMapper.readValue(str, type);
    }

    @Override
    public byte[] objectToBytes(Object obj) {
        String str = objectToString(obj);
        return str == null ? null : str.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Object bytesToObject(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return stringToObject(new String(bytes, StandardCharsets.UTF_8));
    }
}
