package com.cqcnihao.bigx.encrypt.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 明文豁免
 * <p>
 * 优先级最高：即使在 {@code big.encrypt.mode=GLOBAL} 全局加密模式下，
 * 标注了本注解的方法 / 类仍然按明文处理。
 * <p>
 * 典型场景：第三方回调（微信 / 支付宝按自己的签名格式来，无法约定密文信封）、
 * 开放平台 API、健康检查、静态资源。
 *
 * @author bigweb
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmPlain {
}
