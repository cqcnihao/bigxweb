package com.cqcnihao.bigx.common.annotation;

import java.lang.annotation.*;

/**
 * 签名验证注解
 * 标记在Controller方法上，表示该请求需要验证签名
 * 必须在@DecryptRequest之前执行
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface VerifySignature {
    
    /**
     * 签名算法，默认HMAC-SHA256
     */
    String algorithm() default "HMAC-SHA256";
    
    /**
     * 签名密钥配置名称
     */
    String keyConfig() default "default";
    
    /**
     * 签名头名称，默认X-Signature
     */
    String signatureHeader() default "X-Signature";
    
    /**
     * 时间戳头名称，默认X-Timestamp
     */
    String timestampHeader() default "X-Timestamp";
    
    /**
     * 时间戳有效期（毫秒），默认300000（5分钟）
     */
    long timestampValidity() default 300000L;
    
    /**
     * 是否启用，默认true
     */
    boolean enabled() default true;
}