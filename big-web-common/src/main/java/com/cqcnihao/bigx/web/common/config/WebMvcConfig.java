package com.cqcnihao.bigx.web.common.config;

import com.cqcnihao.bigx.utils.interceptor.TraceIdInterceptor;
import com.cqcnihao.bigx.web.common.interceptor.RequestLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceIdInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/druid/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/doc.html",
                        "/webjars/**",
                        "/error"
                )
                .order(1);

        registry.addInterceptor(new RequestLogInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/druid/**",
                        "/swagger-resources/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/doc.html",
                        "/webjars/**",
                        "/static/**",
                        "/favicon.ico"
                )
                .order(2);
    }
}