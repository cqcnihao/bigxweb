package com.cqcnihao.bigx.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 网关统一鉴权过滤器。
 * <p>
 * 鉴权逻辑只保留在这一处；Sa-Token 的 Reactor 上下文由
 * {@link SaTokenGatewayFilterConfig} 中注册的 SaReactorFilter 负责初始化。
 */
@Slf4j
@Component
public class SaTokenAuthGlobalFilter implements GlobalFilter, Ordered {

    /** 精确匹配的白名单路径，避免 startsWith 误放行同前缀接口（如 /auth/loginXXX） */
    private static final Set<String> WHITE_EXACT = Set.of(
            "/auth/login",
            "/auth/captcha",
            "/auth/public-key",
            "/crypto/sm2/public-key",
            "/doc.html",
            "/favicon.ico",
            "/actuator/health",
            "/big-auth/auth/login",
            "/big-auth/auth/captcha",
            "/big-auth/auth/public-key",
            "/big-auth/crypto/sm2/public-key"
    );

    /** 前缀白名单，仅用于明确的路径族 */
    private static final List<String> WHITE_PREFIX = List.of(
            "/v3/api-docs/",
            "/webjars/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, "缺少访问令牌");
        }

        try {
            // 不能用 setTokenValue/checkLogin/getLoginIdAsLong：GlobalFilter 链路上
            // Sa-Token 的 Reactor 上下文不生效，会抛 SaTokenContextException。
            // getLoginIdByToken 直查 DAO（Redis）中的 token→loginId 映射，无需请求上下文。
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                return unauthorized(exchange, "未登录或登录已过期");
            }
            long userId = Long.parseLong(loginId.toString());
            // 必须把 mutate 后的 request 重新挂回 exchange，否则下游收不到 X-User-Id；
            // set 语义覆盖客户端可能伪造的同名头
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> headers.set("X-User-Id", String.valueOf(userId)))
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("鉴权失败 path={}", path, e);
            return unauthorized(exchange, "未登录或登录已过期");
        }
    }

    private boolean isWhiteList(String path) {
        if (WHITE_EXACT.contains(path)) {
            return true;
        }
        return WHITE_PREFIX.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        String json = "{\"success\":false,\"code\":\"401\",\"message\":\"" + message
                + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}