package com.cqcnihao.bigx.common.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(name = "cn.dev33.satoken.stp.StpUtil")
@Import({AuthClientAutoConfiguration.class, DataScopeAutoConfiguration.class})
public class BigAuthAutoConfiguration {
}