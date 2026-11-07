package com.cqcnihao.bigx.common.annotation;

import com.cqcnihao.bigx.common.serializer.DesensitizeSerializer;
import com.cqcnihao.bigx.common.enums.DesensitizeType;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * 数据脱敏注解
 * 用于标记需要脱敏的字段
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = DesensitizeSerializer.class)
public @interface Desensitize {
    
    /**
     * 脱敏类型
     */
    DesensitizeType type() default DesensitizeType.DEFAULT;
}