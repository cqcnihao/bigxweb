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

    /** 网关校验通过后是否继续校验登录态（默认开启，白名单路径除外） */
    private boolean requireLogin = true;

    /** 无需登录态校验的路径（登录、验证码、公钥、文档等公开接口） */
    private List<String> loginExcludePaths = new ArrayList<>();

    public GatewayValidationProperties() {
        requiredHeaders.add("X-Gateway-Name");
        requiredHeaders.add("X-Trace-Id");

        excludePaths.add("/swagger-resources/**");
        excludePaths.add("/v3/api-docs/**");
        excludePaths.add("/swagger-ui/**");
        excludePaths.add("/doc.html");
        excludePaths.add("/webjars/**");
        // 注意：Druid 监控页（/druid/**）默认关闭（druid-defaults.yml 的 DRUID_STAT_ENABLED 默认 false），
        // 且故意不加入网关校验排除清单，避免监控台被直连访问；如确需启用请仅在内网暴露。

        loginExcludePaths.add("/auth/login");
        loginExcludePaths.add("/auth/captcha");
        loginExcludePaths.add("/auth/public-key");
        loginExcludePaths.add("/crypto/sm2/public-key");
        // 客户端 SM2 公钥登记：首次登记必然发生在登录态之前（浏览器本地还没有密钥），
        // 若不排除，客户端将永远无法完成登记，严格验签会拒绝所有加密请求。
        loginExcludePaths.add("/crypto/sm2/client-key");
        // AJ-Captcha 行为验证码：登录前交互，必须匿名可达
        loginExcludePaths.add("/auth/captcha/get");
        loginExcludePaths.add("/auth/captcha/check");
        loginExcludePaths.add("/actuator/health");
        loginExcludePaths.add("/error");
        loginExcludePaths.add("/favicon.ico");
        loginExcludePaths.add("/swagger-resources/**");
        loginExcludePaths.add("/v3/api-docs/**");
        loginExcludePaths.add("/swagger-ui/**");
        loginExcludePaths.add("/doc.html");
        loginExcludePaths.add("/webjars/**");
    }
}