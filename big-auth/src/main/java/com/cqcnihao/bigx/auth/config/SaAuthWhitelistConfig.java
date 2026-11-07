package com.cqcnihao.bigx.auth.config;

import com.cqcnihao.bigx.common.auth.SaInterceptorCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 认证模块自身的匿名访问白名单：
 * 登录、验证码、公钥获取必须在未登录状态下可用，否则永远无法登录。
 */
@Configuration
public class SaAuthWhitelistConfig implements SaInterceptorCustomizer {

    @Override
    public void customize(RequestConfig config) {
        config.getExcludePatterns().add("/auth/login");
        config.getExcludePatterns().add("/auth/captcha");
        config.getExcludePatterns().add("/auth/public-key");
    }
}
