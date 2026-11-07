package com.cqcnihao.bigx.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// scanBasePackages 加入 big-common 的 auth 包：
// 注册 JacksonSaTokenDao/Jackson3SaSerializerTemplate，与 big-auth 共享 Redis token 会话
@SpringBootApplication(scanBasePackages = {"com.cqcnihao.bigx.gateway", "com.cqcnihao.bigx.common.auth"})
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}