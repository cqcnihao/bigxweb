package com.cqcnihao.bigx.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关 CORS 配置
 * <p>
 * 不再使用 {@code allowedOriginPattern("*")} + {@code allowCredentials=true} 的通配组合
 * （OWASP 明确禁止：任意站点都能携带 Cookie/Authorization 发起跨域请求）。
 * 允许来源由 {@code CORS_ALLOWED_ORIGINS}（逗号分隔）显式声明，
 * 未配置时仅放行本地开发地址。
 */
@Slf4j
@Configuration
public class GatewayCorsConfig {

    /** 生产必须通过 CORS_ALLOWED_ORIGINS 显式声明，默认仅本地开发地址 */
    @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : splitOrigins(allowedOrigins)) {
            config.addAllowedOrigin(origin);
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        // 下游服务已关闭自身 CORS，原先由它们暴露的链路追踪头统一在这里暴露
        config.addExposedHeader("X-Trace-Id");

        log.info("[CORS] 网关放行来源：{}", config.getAllowedOrigins());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    private static List<String> splitOrigins(String raw) {
        List<String> origins = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return origins;
        }
        for (String item : raw.split(",")) {
            String origin = item.trim();
            if (!origin.isEmpty()) {
                origins.add(origin);
            }
        }
        return origins;
    }
}