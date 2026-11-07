package com.cqcnihao.bigx.common.annotation;

import java.lang.annotation.*;

/**
 * 请求解密注解
 * 标记在Controller方法上，表示该请求需要解密
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DecryptRequest {
    
    /**
     * 加密算法，默认AES/GCM/NoPadding
     */
    String algorithm() default "AES/GCM/NoPadding";
    
    /**
     * 密钥配置名称，默认使用全局配置
     */
    String keyConfig() default "default";
    
    /**
     * 是否启用，默认true
     */
    boolean enabled() default true;
}