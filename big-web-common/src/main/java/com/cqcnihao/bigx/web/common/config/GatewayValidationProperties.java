package com.cqcnihao.bigx.web.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关验证配置属性
 * 用于配置下游服务如何验证请求是否经过网关
 * 
 * 使用示例（application.yml）：
 * 
 * gateway:
 *   validation:
 *     enabled: true                    # 是否启用网关验证
 *     strict-mode: true                # 严格模式（true=拒绝所有直接访问，false=仅警告）
 *     required-headers:                # 必须存在的请求头
 *       - X-Gateway-Name
 *       - X-Trace-Id
 *     exclude-paths:                   # 排除的路径（不进行网关验证）
 *       - /actuator/**
 *       - /health
 *       - /swagger-resources/**
 *       - /v3/api-docs/**
 * 
 * @author bigweb
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.validation")
public class GatewayValidationProperties {

    private boolean enabled = true;

    private boolean strictMode = true;

    private List<String> requiredHeaders = new ArrayList<>();

    private List<String> excludePaths = new ArrayList<>();

    private boolean signatureEnabled = true;

    private String secret;

    private long signToleranceSeconds = 60;

    private String internalHeaderName = "X-Internal-Service-Call";

    private String internalHeaderValue;

    public GatewayValidationProperties() {
        requiredHeaders.add("X-Gateway-Name");
        requiredHeaders.add("X-Trace-Id");

        excludePaths.add("/swagger-resources/**");
        excludePaths.add("/v3/api-docs/**");
        excludePaths.add("/swagger-ui/**");
        excludePaths.add("/doc.html");
        excludePaths.add("/webjars/**");
        excludePaths.add("/druid/**");
    }
}