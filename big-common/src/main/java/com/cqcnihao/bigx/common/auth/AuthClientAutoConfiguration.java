package com.cqcnihao.bigx.common.auth;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@ConditionalOnClass(SaInterceptor.class)
public class AuthClientAutoConfiguration {

    @Bean
    @ConditionalOnBean(AuthStpInterface.class)
    @ConditionalOnMissingBean(StpInterface.class)
    public StpInterface stpInterface(AuthStpInterface authStpInterface) {
        return new DelegatingStpInterface(authStpInterface);
    }

    @Configuration
    @ConditionalOnClass(WebMvcConfigurer.class)
    public static class SaInterceptorConfig implements WebMvcConfigurer {

        private final List<SaInterceptorCustomizer> customizers;
        private final boolean defaultAutoRegister;
        private final boolean defaultEnableLoginCheck;

        public SaInterceptorConfig(List<SaInterceptorCustomizer> customizers,
                                   @org.springframework.beans.factory.annotation.Value("${bigweb.auth.sa-interceptor.auto-register:true}")
                                   boolean defaultAutoRegister,
                                   @org.springframework.beans.factory.annotation.Value("${bigweb.auth.sa-interceptor.enable-login-check:true}")
                                   boolean defaultEnableLoginCheck) {
            this.customizers = customizers;
            this.defaultAutoRegister = defaultAutoRegister;
            this.defaultEnableLoginCheck = defaultEnableLoginCheck;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            SaInterceptorCustomizer.RequestConfig config = new SaInterceptorCustomizer.RequestConfig();
            config.setAutoRegister(defaultAutoRegister);
            config.setEnableLoginCheck(defaultEnableLoginCheck);
            for (SaInterceptorCustomizer c : customizers) {
                c.customize(config);
            }
            if (config.isAutoRegister()) {
                cn.dev33.satoken.interceptor.SaInterceptor interceptor;
                if (config.getHandler() != null) {
                    interceptor = new cn.dev33.satoken.interceptor.SaInterceptor(obj -> {
                        if (obj instanceof cn.dev33.satoken.router.SaRouter router) {
                            config.getHandler().handle(router);
                        }
                    });
                } else {
                    interceptor = new cn.dev33.satoken.interceptor.SaInterceptor(handle -> {
                        if (config.isEnableLoginCheck()) {
                            cn.dev33.satoken.stp.StpUtil.checkLogin();
                        }
                    });
                }
                registry.addInterceptor(interceptor)
                        .addPathPatterns(config.getIncludePatterns())
                        .excludePathPatterns(config.getExcludePatterns())
                        .order(10);
            }
        }
    }
}