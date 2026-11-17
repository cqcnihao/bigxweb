package com.cqcnihao.bigx.auth.config;

import com.cqcnihao.bigx.common.auth.SaInterceptorCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 认证模块自身的匿名访问白名单：
 * 登录、验证码、公钥获取必须在未登录状态下可用，否则永远无法登录。
 * <p>
 * 客户端 SM2 公钥登记同样必须匿名可用：首次登记发生在登录态建立之前，
 * 而严格验签要求所有加密请求携带签名——若这里不放行，
 * 客户端拿不到 clientKeyId，登录请求就会被 CODE_SIGN_INVALID 拒绝，形成死循环。
 */
@Configuration
public class SaAuthWhitelistConfig implements SaInterceptorCustomizer {

    @Override
    public void customize(RequestConfig config) {
        config.getExcludePatterns().add("/auth/login");
        config.getExcludePatterns().add("/auth/captcha");
        // AJ-Captcha 行为验证码：登录前交互，必须匿名可达
        config.getExcludePatterns().add("/auth/captcha/get");
        config.getExcludePatterns().add("/auth/captcha/check");
        config.getExcludePatterns().add("/auth/public-key");
        config.getExcludePatterns().add("/crypto/sm2/client-key");
        config.getExcludePatterns().add("/crypto/sm2/client-key/**");
        config.getExcludePatterns().add("/crypto/sm2/public-key");
    }
}