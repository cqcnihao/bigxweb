package com.cqcnihao.bigx.encrypt.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开启国密加解密
 * <p>
 * 标注在 Controller 方法或类上：标注在类上时该 Controller 全部接口生效，
 * 方法级标注优先于类级标注。
 * <p>
 * 典型用法：
 * <pre>
 * &#64;PostMapping("/account")
 * &#64;SmEncrypt(sign = true)
 * public Result&lt;AccountVO&gt; query(&#64;RequestBody AccountQuery query) { ... }
 * </pre>
 *
 * @author bigweb
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmEncrypt {

    /** 是否解密请求体 */
    boolean request() default true;

    /** 是否加密响应体 */
    boolean response() default true;

    /** 是否强制校验 SM2 签名（需要配置 big.encrypt.sm2.public-key） */
    boolean sign() default false;

    /** 对称算法变换名，留空使用全局配置 */
    String alg() default "";

    /** 密钥版本，留空使用全局默认 keyId */
    String keyId() default "";

    /** 是否进行时间戳 / nonce 防重放校验 */
    boolean replay() default true;

    /** 是否启用（false 时该接口按明文处理） */
    boolean enabled() default true;
}
