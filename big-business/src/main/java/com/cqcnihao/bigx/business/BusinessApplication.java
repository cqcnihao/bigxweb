package com.cqcnihao.bigx.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
        "com.cqcnihao.bigx.business",
        "com.cqcnihao.bigx.common",
        "com.cqcnihao.bigx.encrypt"
})
@MapperScan({"com.cqcnihao.bigx.business.mapper", "com.cqcnihao.bigx.license.mapper"})
@EnableDiscoveryClient
public class BusinessApplication {
    public static void main(String[] args) {
        SpringApplication.run(BusinessApplication.class, args);
    }
}