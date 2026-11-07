package com.cqcnihao.bigx.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
        "com.cqcnihao.bigx.auth",
        "com.cqcnihao.bigx.common",
        "com.cqcnihao.bigx.encrypt"
})
@MapperScan("com.cqcnihao.bigx.auth.mapper")
@EnableDiscoveryClient
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}