package com.cqcnihao.bigx.encrypt.support;

import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.encrypt.annotation.SmPlain;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 加解密策略解析器
 * <p>
 * 统一裁决「某个 Controller 方法到底要不要解密请求 / 加密响应」，
 * 供 RequestBodyAdvice 与 ResponseBodyAdvice 共用，保证两个方向判定一致。
 * <p>
 * 优先级：{@code @SmPlain} &gt; 方法级 {@code @SmEncrypt} &gt; 类级 {@code @SmEncrypt} &gt; 全局模式
 *
 * @author bigweb
 */
public final class EncryptPolicy {

    private EncryptPolicy() {
    }

    /**
     * 缓存条目：把策略与它对应的 Controller 方法绑定
     * <p>
     * 必须绑定方法，否则异常流程会出现串味：异常响应由 {@code @ExceptionHandler} 方法产生，
     * 如果直接复用拦截器为「原 Controller 方法」缓存的策略，错误响应也会被加密，
     * 前端就拿不到可读的 code/message 了。
     */
    public static class PolicyEntry {
        private final Method method;
        private final Decision decision;

        public PolicyEntry(Method method, Decision decision) {
            this.method = method;
            this.decision = decision;
        }

        public Method getMethod() {
            return method;
        }

        public Decision getDecision() {
            return decision;
        }
    }

    /**
     * 判定结果
     */
    public static class Decision {
        private final boolean decrypt;
        private final boolean encrypt;
        private final boolean sign;
        private final boolean replay;
        private final String alg;
        private final String keyId;

        Decision(boolean decrypt, boolean encrypt, boolean sign, boolean replay, String alg, String keyId) {
            this.decrypt = decrypt;
            this.encrypt = encrypt;
            this.sign = sign;
            this.replay = replay;
            this.alg = alg;
            this.keyId = keyId;
        }

        public static Decision plain() {
            return new Decision(false, false, false, false, null, null);
        }

        public boolean isDecrypt() {
            return decrypt;
        }

        public boolean isEncrypt() {
            return encrypt;
        }

        public boolean isSign() {
            return sign;
        }

        public boolean isReplay() {
            return replay;
        }

        public String getAlg() {
            return alg;
        }

        public String getKeyId() {
            return keyId;
        }

        public boolean isEmpty() {
            return !decrypt && !encrypt;
        }
    }

    /**
     * 解析策略
     *
     * @param method     Controller 方法，可能为 null
     * @param containingClass 方法所在类
     * @param properties 全局配置
     * @param uri        当前请求路径，用于白名单前缀匹配
     */
    public static Decision resolve(Method method, Class<?> containingClass,
                                  EncryptProperties properties, String uri) {
        if (properties == null || !properties.isEnabled()
                || properties.getMode() == EncryptProperties.Mode.OFF) {
            return Decision.plain();
        }
        if (isSkipped(uri, properties.getSkipPaths())) {
            return Decision.plain();
        }

        // 0. @ControllerAdvice 类（全局异常处理器 / 统一包装器）永远明文：
        //    否则全局加密模式下，异常响应也会被加密，前端拿不到可读的 code/message
        if (containingClass != null
                && AnnotatedElementUtils.hasAnnotation(containingClass, ControllerAdvice.class)) {
            return Decision.plain();
        }

        boolean global = properties.getMode() == EncryptProperties.Mode.GLOBAL;

        // 1. @SmPlain 优先级最高
        if (method != null && AnnotatedElementUtils.hasAnnotation(method, SmPlain.class)) {
            return Decision.plain();
        }
        if (containingClass != null && AnnotatedElementUtils.hasAnnotation(containingClass, SmPlain.class)) {
            return Decision.plain();
        }

        // 2. 方法级 @SmEncrypt 优先，其次类级
        SmEncrypt cfg = null;
        if (method != null) {
            cfg = AnnotatedElementUtils.findMergedAnnotation(method, SmEncrypt.class);
        }
        if (cfg == null && containingClass != null) {
            cfg = AnnotatedElementUtils.findMergedAnnotation(containingClass, SmEncrypt.class);
        }

        if (cfg != null) {
            if (!cfg.enabled()) {
                return Decision.plain();
            }
            return new Decision(
                    cfg.request(),
                    cfg.response(),
                    cfg.sign() || properties.getSm2().isVerifySignature(),
                    cfg.replay(),
                    nullIfBlank(cfg.alg()),
                    nullIfBlank(cfg.keyId()));
        }

        // 3. 全局模式兜底
        if (global) {
            return new Decision(
                    true,
                    true,
                    properties.getSm2().isVerifySignature(),
                    true,
                    null,
                    null);
        }

        return Decision.plain();
    }

    /**
     * 路径白名单前缀匹配
     */
    public static boolean isSkipped(String uri, String[] skipPaths) {
        if (uri == null || skipPaths == null || skipPaths.length == 0) {
            return false;
        }
        return Arrays.stream(skipPaths)
                .filter(StringUtils::hasText)
                .anyMatch(uri::startsWith);
    }

    private static String nullIfBlank(String v) {
        return StringUtils.hasText(v) ? v : null;
    }
}