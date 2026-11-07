package com.cqcnihao.bigx.web.common.filter;

import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.web.common.config.GatewayValidationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class GatewayValidationFilter extends OncePerRequestFilter {

    private static final String HEADER_GATEWAY_NAME = "X-Gateway-Name";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_GATEWAY_VERSION = "X-Gateway-Version";
    private static final String HEADER_REQUEST_FROM = "X-Request-From";
    private static final String HEADER_GATEWAY_TIMESTAMP = "X-Gateway-Timestamp";
    private static final String HEADER_GATEWAY_SIGN = "X-Gateway-Sign";

    private final GatewayValidationProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayValidationFilter(GatewayValidationProperties properties,
                                   ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.properties = properties;
        ObjectMapper existing = objectMapperProvider.getIfAvailable();
        this.objectMapper = existing != null ? existing : new ObjectMapper();
    }

    private static final String SECRET_PLACEHOLDER = "REPLACE_ME_IN_PROD_GATEWAY_SECRET";

    @PostConstruct
    public void securityCheck() {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank() || SECRET_PLACEHOLDER.equals(secret)) {
            log.warn("╔══════════════════════════════════════════════════════════════╗");
            log.warn("║  [SECURITY] 网关签名密钥未配置或仍为默认占位符!                 ║");
            log.warn("║  生产环境必须通过环境变量 GATEWAY_VALIDATION_SECRET 或         ║");
            log.warn("║  application.yml gateway.validation.secret 配置真实密钥。      ║");
            log.warn("╚══════════════════════════════════════════════════════════════╝");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestURI = request.getRequestURI();

        if (isExcludedPath(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isInternalCall(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String gatewayName = request.getHeader(HEADER_GATEWAY_NAME);
        String traceId = request.getHeader(HEADER_TRACE_ID);

        if (gatewayName == null || gatewayName.isEmpty()) {
            log.warn("[GatewayFilter] 缺少网关头 | Path: {} | IP: {}", requestURI, getClientIp(request));
            if (properties.isStrictMode()) {
                reject(response, 403, "请求必须经过网关");
                return;
            }
        }

        if (properties.getRequiredHeaders().contains(HEADER_TRACE_ID) &&
            (traceId == null || traceId.isEmpty())) {
            log.warn("[GatewayFilter] 缺少 TraceId | Path: {}", requestURI);
            reject(response, 403, "请求缺少 TraceId");
            return;
        }

        if (properties.isSignatureEnabled()) {
            String timestamp = request.getHeader(HEADER_GATEWAY_TIMESTAMP);
            String signature = request.getHeader(HEADER_GATEWAY_SIGN);

            if (timestamp == null || timestamp.isEmpty() || signature == null || signature.isEmpty()) {
                log.warn("[GatewayFilter] 缺少签名头 | Path: {}", requestURI);
                reject(response, 403, "网关签名缺失");
                return;
            }

            try {
                long ts = Long.parseLong(timestamp);
                long diff = Math.abs(System.currentTimeMillis() - ts);
                if (diff > properties.getSignToleranceSeconds() * 1000) {
                    log.warn("[GatewayFilter] 请求过期 | Path: {} | diff={}ms", requestURI, diff);
                    reject(response, 403, "网关请求过期");
                    return;
                }

                String method = request.getMethod();
                String signPayload = gatewayName + "|" + timestamp + "|" + method + "|" + requestURI;
                String expected = hmacSha256Hex(properties.getSecret(), signPayload);

                if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
                    log.warn("[GatewayFilter] 签名校验失败 | Path: {}", requestURI);
                    reject(response, 403, "网关签名校验失败");
                    return;
                }
            } catch (NumberFormatException e) {
                log.warn("[GatewayFilter] 时间戳格式错误 | Path: {} | ts={}", requestURI, timestamp);
                reject(response, 403, "网关时间戳无效");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcludedPath(String requestURI) {
        if (properties.getExcludePaths() == null || properties.getExcludePaths().isEmpty()) {
            return false;
        }
        for (String pattern : properties.getExcludePaths()) {
            if (pathMatcher.match(pattern, requestURI)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInternalCall(HttpServletRequest request) {
        String headerName = properties.getInternalHeaderName();
        if (headerName == null || headerName.isEmpty()) {
            return false;
        }
        String headerValue = request.getHeader(headerName);
        if (headerValue == null || headerValue.isEmpty()) {
            return false;
        }
        String expected = properties.getInternalHeaderValue();
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), headerValue.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<?> result = Result.error(String.valueOf(status), message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }
}