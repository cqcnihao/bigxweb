package com.cqcnihao.bigx.utils;

import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix ="minio")
public class MinioProperties {

    private String endpoint = "http://localhost:9005";
    private String accessKey;
    private String secretKey;
    private String bucketName;

    private String region = "us-east-1";

    private boolean secure = false;

    private int connectTimeout = 10000;

    private int writeTimeout = 60000;

    private int readTimeout = 10000;

    @PostConstruct
    public void init() {
        log.info("MinioProperties init start");
        log.info("Endpoint: {}", endpoint);
        log.debug("AccessKey: {}", accessKey != null ? "***" : "null");
        log.info("bucketName: {}", bucketName);
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            log.warn("MinIO accessKey/secretKey 未配置，请在 application.yml 中设置 minio.access-key 和 minio.secret-key");
        }
        log.info("MinioProperties init end");
    }

    @Bean
    public MinioClient minioClient() {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("MinIO accessKey/secretKey 未配置，无法创建 MinioClient");
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}