package com.cqcnihao.bigx.encrypt.interceptor;

import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import com.cqcnihao.bigx.encrypt.support.EncryptContext;
import com.cqcnihao.bigx.encrypt.support.EncryptPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 国密拦截器
 * <p>
 * <b>定位：只做「读 header 就够」的前置校验，不碰请求体。</b>
 * <p>
 * 为什么解密/加密不能交给拦截器（这是拦截器与 Advice 的核心分界）：
 * <ul>
 *   <li><b>请求解密做不到</b>：请求体流只能读一次，拦截器读完参数解析器就读不到了；
 *       而且拦截器运行在参数绑定之前，改不了 {@code @RequestBody} 绑定出来的对象。</li>
 *   <li><b>响应加密做不到</b>：{@code postHandle} 时对象还没序列化，拿不到最终 JSON；
 *       要改只能劫持输出流，会破坏 SSE / 文件下载 / 分块传输。</li>
 * </ul>
 * 所以拦截器在这里承担它真正擅长的三件事：
 * <ol>
 *   <li>白名单路径短路，标记 {@code SKIPPED} 让后续 Advice 直接放行；</li>
 *   <li>时间戳新鲜性 + nonce 防重放（数据来自 header，无需读 body）；</li>
 *   <li>预留埋点位置（加密成功率、耗时可在此接入 Micrometer）。</li>
 * </ol>
 *
 * @author bigweb
 */
@Slf4j
public class SmCryptoInterceptor implements HandlerInterceptor {

    private final EncryptProperties properties;
    private final SmCryptoService cryptoService;

    public SmCryptoInterceptor(EncryptProperties properties, SmCryptoService cryptoService) {
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || properties.getMode() == EncryptProperties.Mode.OFF) {
            return true;
        }
        String uri = request.getRequestURI();
        if (EncryptPolicy.isSkipped(uri, properties.getSkipPaths())) {
            EncryptContext.markSkipped(request);
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        EncryptPolicy.Decision decision = EncryptPolicy.resolve(
                handlerMethod.getMethod(),
                handlerMethod.getBeanType(),
                properties,
                uri);
        request.setAttribute(EncryptContext.ATTR_POLICY,
                new EncryptPolicy.PolicyEntry(handlerMethod.getMethod(), decision));
        if (decision.isEmpty()) {
            return true;
        }

        // header 级新鲜性校验；body 里的 timestamp/nonce 由 RequestBodyAdvice 兜底
        String tsHeader = request.getHeader(EncryptContext.HEADER_TIMESTAMP);
        String nonceHeader = request.getHeader(EncryptContext.HEADER_NONCE);
        if (StringUtils.hasText(tsHeader) || StringUtils.hasText(nonceHeader)) {
            Long timestamp = null;
            if (StringUtils.hasText(tsHeader)) {
                try {
                    timestamp = Long.parseLong(tsHeader.trim());
                } catch (NumberFormatException e) {
                    throw new com.cqcnihao.bigx.encrypt.exception.CryptoException(
                            com.cqcnihao.bigx.encrypt.exception.CryptoException.CODE_TIMESTAMP_EXPIRED,
                            "时间戳格式非法");
                }
            }
            if (decision.isReplay()) {
                if (hasRequestBody(handlerMethod)) {
                    // 有请求体时 nonce 由 RequestBodyAdvice 在验签/MAC 通过后再消费，
                    // 这里只校验时间戳，防止伪造请求在验签前抢先烧掉合法 nonce
                    cryptoService.checkFreshness(timestamp, null);
                } else {
                    cryptoService.checkFreshness(timestamp, nonceHeader);
                    // 标记已校验，防止 Advice 重复登记同一个 nonce 被误判为重放
                    EncryptContext.markFreshnessDone(request);
                }
            }
        }
        return true;
    }

    /** 判断处理方法是否含 @RequestBody 参数，决定 nonce 由拦截器还是 Advice 消费 */
    private static boolean hasRequestBody(HandlerMethod handlerMethod) {
        for (org.springframework.core.MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        request.removeAttribute(EncryptContext.ATTR_POLICY);
        request.removeAttribute(EncryptContext.ATTR_FRESHNESS_DONE);
        request.removeAttribute(EncryptContext.ATTR_SKIPPED);
    }
}
