package com.cqcnihao.bigx.encrypt.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具
 * <p>
 * 拦截器与两个 Advice 之间需要共享少量状态（已跳过的路径、已做过的新鲜性校验），
 * 统一放在 Request attribute 里，避免重复校验导致 nonce 误判为重放。
 *
 * @author bigweb
 */
public final class EncryptContext {

    /** 请求头：客户端时间戳（毫秒） */
    public static final String HEADER_TIMESTAMP = "X-Sm-Timestamp";

    /** 请求头：随机串 */
    public static final String HEADER_NONCE = "X-Sm-Nonce";

    /** 请求头：SM2 签名 */
    public static final String HEADER_SIGN = "X-Sm-Signature";

    /** 请求头：SM4 密钥版本 */
    public static final String HEADER_KEY_ID = "X-Sm-Key-Id";

    /** 请求头：客户端 SM2 密钥标识（服务端登记时签发） */
    public static final String HEADER_CLIENT_KEY_ID = "X-Sm-Client-Key-Id";

    /** 属性：本次请求已命中白名单 / 明文放行 */
    public static final String ATTR_SKIPPED = EncryptContext.class.getName() + ".SKIPPED";

    /** 属性：新鲜性（时间戳 + nonce）已由拦截器校验过，Advice 不再重复校验 */
    public static final String ATTR_FRESHNESS_DONE = EncryptContext.class.getName() + ".FRESHNESS_DONE";

    /** 属性：本次请求实际使用的加解密策略 */
    public static final String ATTR_POLICY = EncryptContext.class.getName() + ".POLICY";

    private EncryptContext() {
    }

    public static HttpServletRequest currentRequest() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        return (ra instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    public static String currentUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    public static void markSkipped(HttpServletRequest request) {
        if (request != null) {
            request.setAttribute(ATTR_SKIPPED, Boolean.TRUE);
        }
    }

    public static boolean isSkipped(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(ATTR_SKIPPED));
    }

    public static void markFreshnessDone(HttpServletRequest request) {
        if (request != null) {
            request.setAttribute(ATTR_FRESHNESS_DONE, Boolean.TRUE);
        }
    }

    public static boolean isFreshnessDone(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(ATTR_FRESHNESS_DONE));
    }
}