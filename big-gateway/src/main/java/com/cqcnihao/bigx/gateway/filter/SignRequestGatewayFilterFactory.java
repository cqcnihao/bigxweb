package com.cqcnihao.bigx.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 为转发请求注入网关标识头并计算 HMAC-SHA256 签名，
 * 对应下游 big-web-common 的 GatewayValidationFilter 校验逻辑。
 * <p>
 * 必须配置在路由的 StripPrefix 之后：签名负载中的 URI 必须是
 * 下游服务实际收到的路径（StripPrefix 会先行改写请求路径）。
 */
@Component
public class SignRequestGatewayFilterFactory extends AbstractGatewayFilterFactory<SignRequestGatewayFilterFactory.Config> {

    private static final String GATEWAY_NAME = "big-gateway";
    private static final String GATEWAY_VERSION = "1.0.0";

    @Value("${gateway.validation.secret:}")
    private String secret;

    public SignRequestGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String traceId = GATEWAY_NAME + "-" + UUID.randomUUID();
            // 此处拿到的 path 已被前面的 StripPrefix 改写，与下游 request.getRequestURI() 一致
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();
            String signature = hmacSha256Hex(secret,
                    GATEWAY_NAME + "|" + timestamp + "|" + method + "|" + path);

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    // set 语义覆盖客户端可能伪造的同名校验头
                    .headers(headers -> {
                        headers.set("X-Gateway-Name", GATEWAY_NAME);
                        headers.set("X-Gateway-Version", GATEWAY_VERSION);
                        headers.set("X-Request-From", GATEWAY_NAME);
                        headers.set("X-Trace-Id", traceId);
                        headers.set("X-Gateway-Timestamp", timestamp);
                        headers.set("X-Gateway-Sign", signature);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        };
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
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    public static class Config {
    }
}
