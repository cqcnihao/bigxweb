package com.cqcnihao.bigx.web.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 * 统一处理所有 Web 模块的跨域请求
 * <p>
 * 默认<b>关闭</b>（{@code matchIfMissing=false}）：跨域统一由网关 CorsWebFilter 处理。
 * 下游若再添加一次 CORS 响应头，会产生重复的 Access-Control-Allow-Origin，
 * 浏览器会以"包含多个值"为由直接拒绝跨域请求。
 * 仅在直连下游调试时显式设置 {@code big.web.cors-enabled=true}。
 *
 * @author bigweb
 */
@Configuration
@ConditionalOnProperty(prefix = "big.web", name = "cors-enabled", havingValue = "true", matchIfMissing = false)
public class CorsConfig {

    /** 直连调试时允许的来源，默认仅本地开发地址 */
    @Value("${big.web.cors-allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 显式声明来源，不再使用 "*" 通配（通配 + allowCredentials 是 OWASP 禁止的反模式）
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                config.addAllowedOrigin(trimmed);
            }
        }

        // 允许所有请求头
        config.addAllowedHeader("*");
        
        // 允许所有请求方法
        config.addAllowedMethod("*");
        
        // 允许携带认证信息（cookies）
        config.setAllowCredentials(true);
        
        // 预检请求的有效期（秒）
        config.setMaxAge(3600L);
        
        // 暴露的响应头
        config.addExposedHeader("X-Trace-Id");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}