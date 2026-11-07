package com.cqcnihao.bigx.web.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.context.annotation.Bean;

/**
 * CORS 跨域配置
 * 统一处理所有 Web 模块的跨域请求
 * <p>
 * 经网关访问时必须在配置中关闭（big.web.cors-enabled=false）：
 * 网关的 CorsWebFilter 已经添加 CORS 响应头，下游再加一次会产生重复的
 * Access-Control-Allow-Origin，浏览器会以"包含多个值"为由直接拒绝跨域请求。
 *
 * @author bigweb
 */
@Configuration
@ConditionalOnProperty(prefix = "big.web", name = "cors-enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有域名访问（生产环境应该配置具体域名）
        config.addAllowedOriginPattern("*");
        
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
