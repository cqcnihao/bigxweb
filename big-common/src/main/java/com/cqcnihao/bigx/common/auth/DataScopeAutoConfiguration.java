package com.cqcnihao.bigx.common.auth;

import com.cqcnihao.bigx.common.datascope.DataScopeInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(DataScopeInterceptor.class)
public class DataScopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataScopeInterceptor dataScopeInterceptor() {
        return new DataScopeInterceptor();
    }
}