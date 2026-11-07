package com.cqcnihao.bigx.web.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cookie安全配置
 * 配置SameSite等安全属性，防止CSRF攻击
 * 
 * 注意：此配置依赖 spring-session，通过条件属性控制是否加载
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.session.web.http.CookieSerializer")
@ConditionalOnProperty(name = "bigweb.cookie-config.enabled", havingValue = "true", matchIfMissing = false)
public class CookieConfig {
    
    /**
     * 配置Cookie序列化器
     * 设置SameSite属性防止CSRF攻击
     */
    @Bean
    public org.springframework.session.web.http.CookieSerializer cookieSerializer() {
        org.springframework.session.web.http.DefaultCookieSerializer serializer = 
            new org.springframework.session.web.http.DefaultCookieSerializer();
        
        // SameSite属性：Strict（严格）或 Lax（宽松）
        // Strict: 完全禁止第三方Cookie，最安全但可能影响用户体验
        // Lax: 允许部分场景发送Cookie，平衡安全性和用户体验
        serializer.setSameSite("Lax");
        
        // Secure属性：仅HTTPS传输
        // 生产环境建议设置为true
        serializer.setUseSecureCookie(false); // 开发环境设为false，生产环境改为true
        
        // HttpOnly属性：禁止JavaScript访问Cookie
        serializer.setUseHttpOnlyCookie(true);
        
        // Cookie路径
        serializer.setCookiePath("/");
        
        // Cookie域名（可选）
        // serializer.setDomainName("your-domain.com");
        
        return serializer;
    }
}