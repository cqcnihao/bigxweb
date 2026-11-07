package com.cqcnihao.bigx.gateway.filter;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 SaReactorFilter 仅为初始化 Sa-Token 的 Reactor 请求上下文
 * （没有它，GlobalFilter 中调用 StpUtil 会抛 NotWebContextException）。
 * <p>
 * 鉴权与白名单判断统一由 {@link SaTokenAuthGlobalFilter} 完成，这里不再重复设置 setAuth。
 */
@Configuration
public class SaTokenGatewayFilterConfig {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter().addInclude("/**");
    }
}
