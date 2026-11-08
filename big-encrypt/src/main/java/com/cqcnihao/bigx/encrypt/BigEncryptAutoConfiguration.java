package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.encrypt.advice.SmDecryptRequestBodyAdvice;
import com.cqcnihao.bigx.encrypt.advice.SmEncryptResponseBodyAdvice;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.controller.Sm2KeyController;
import com.cqcnihao.bigx.encrypt.interceptor.SmCryptoInterceptor;
import com.cqcnihao.bigx.encrypt.service.AesCryptoService;
import com.cqcnihao.bigx.encrypt.service.MessageCryptoService;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import com.cqcnihao.bigx.encrypt.support.InMemoryReplayGuard;
import com.cqcnihao.bigx.encrypt.support.ReplayGuard;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

import java.util.Arrays;

/**
 * big-Encrypt 自动装配
 * <p>
 * 采用与 {@code BigAuthAutoConfiguration} 一致的范式：
 * {@code @AutoConfiguration} + {@code @ConditionalOnClass} + {@code @ConditionalOnMissingBean}
 * + {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 * <p>
 * 总开关：{@code big.encrypt.enabled=false} 可整体关闭；
 * 默认 {@code mode=ANNOTATION}，即只有标注 {@code @SmEncrypt} 的接口才加解密，
 * 对存量接口零影响。
 *
 * @author bigweb
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.bouncycastle.jce.provider.BouncyCastleProvider")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "big.encrypt", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EncryptProperties.class)
@Import(BigEncryptAutoConfiguration.SmCryptoWebMvcConfigurer.class)
public class BigEncryptAutoConfiguration {

    /** 与各服务 yml 中 SM4 密钥的 dev 兜底默认值保持一致，仅限本地开发 */
    public static final String DEV_DEFAULT_SM4_KEY = "ZGV2LXNtNC1kZXYta2V5IQ==";

    private final Environment environment;

    public BigEncryptAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        try {
            BcProvider.provider();
        } catch (Exception e) {
            log.error("BouncyCastle Provider 注册失败，国密加解密将不可用", e);
        }
        checkProdKeyGuard();
    }

    /**
     * prod profile 下检测到 dev 默认 SM4 密钥时拒绝启动（与网关签名密钥守卫同范式）；
     * 其他环境打印错误日志强提醒。
     */
    private void checkProdKeyGuard() {
        var sm4 = environment.getProperty("big.encrypt.sm4.keys.v1", "");
        boolean devDefault = sm4.isBlank() || DEV_DEFAULT_SM4_KEY.equals(sm4);
        if (!devDefault) {
            return;
        }
        boolean prod = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equalsIgnoreCase);
        if (prod) {
            throw new IllegalStateException("[SECURITY] 生产环境必须通过环境变量 SM4_KEY_V1 注入真实的 SM4 密钥，"
                    + "拒绝使用 dev 默认密钥或空密钥启动");
        }
        log.error("[SECURITY] 加密模块已启用但 SM4 密钥为空或 dev 默认值，仅限本地开发使用；"
                + "生产环境必须通过环境变量 SM4_KEY_V1（16 字节 Base64）覆盖");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")
    public ReplayGuard smReplayGuard(EncryptProperties properties) {
        return inMemoryReplayGuard(properties);
    }

    private ReplayGuard inMemoryReplayGuard(EncryptProperties properties) {
        log.warn("未检测到可用的 Redis，防重放回退为 JVM 内存版 InMemoryReplayGuard —— 仅单实例部署有效。" +
                "多实例/集群环境请引入 spring-boot-starter-data-redis，否则重放防护将静默失效。");
        return new InMemoryReplayGuard(Math.max(properties.getTimestampValidity() * 2, 60_000L));
    }

    /**
     * 加解密门面：按 {@code big.encrypt.algorithm} 选择对称算法实现，
     * 默认 SM4（国密合规）；AES 用于与存量 AES 系统互通，复用 big-common 的 AesCryptoUtil。
     * <p>
     * 两种实现共用同一套协议（SM2 签名、SM3 防重放、多版本 keyId），
     * 因此 Advice / 拦截器无需感知算法差异。
     */
    @Bean
    @ConditionalOnMissingBean
    public SmCryptoService smCryptoService(EncryptProperties properties, ReplayGuard replayGuard) {
        if (properties.getAlgorithm() == EncryptProperties.Algorithm.AES) {
            log.info("big-encrypt 对称算法：AES-GCM（big.encrypt.algorithm=AES）");
            return new AesCryptoService(properties, replayGuard);
        }
        return new SmCryptoService(properties, replayGuard);
    }

    @Bean
    @ConditionalOnMissingBean
    public SmDecryptRequestBodyAdvice smDecryptRequestBodyAdvice(EncryptProperties properties,
                                                                 MessageCryptoService cryptoService,
                                                                 ObjectProvider<ObjectMapper> objectMapper) {
        return new SmDecryptRequestBodyAdvice(properties, cryptoService,
                resolveObjectMapper(objectMapper));
    }

    @Bean
    @ConditionalOnMissingBean
    public SmEncryptResponseBodyAdvice smEncryptResponseBodyAdvice(EncryptProperties properties,
                                                                   MessageCryptoService cryptoService,
                                                                   ObjectProvider<ObjectMapper> objectMapper) {
        return new SmEncryptResponseBodyAdvice(properties, cryptoService,
                resolveObjectMapper(objectMapper));
    }

    /**
     * 优先复用容器中已有的 ObjectMapper（保留业务方自定义序列化器如 @Desensitize），
     * 的自动配置（如 spring.jackson.* 属性），比裸 {@code new ObjectMapper()} 更安全。
     */
    private static ObjectMapper resolveObjectMapper(ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper existing = objectMapper.getIfAvailable();
        if (existing != null) {
            return existing;
        }
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "big.encrypt", name = "interceptor-enabled",
            havingValue = "true", matchIfMissing = true)
    public SmCryptoInterceptor smCryptoInterceptor(EncryptProperties properties,
                                                   SmCryptoService cryptoService) {
        return new SmCryptoInterceptor(properties, cryptoService);
    }

    /**
     * SM2 公钥下发端点（混合加密握手第一步）
     * <p>
     * 这是 {@link com.cqcnihao.bigx.encrypt.controller.Sm2KeyController} 的<b>唯一注册点</b>。
     * 该 Controller 是 {@code final} 且带类级 {@code @ConditionalOnExpression}，
     * 因此：既不能被继承后再声明，也不会被扫描到 {@code com.cqcnihao} 的模块绕过开关注册。
     * 这里再加 {@code @ConditionalOnMissingBean} 兜底：若业务模块确实自行声明了同类型 Bean，
     * 本处自动退让，保证上下文中始终只有一个实例、一份映射。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "big.encrypt.sm2", name = "expose-key-endpoint",
            havingValue = "true", matchIfMissing = true)
    public Sm2KeyController sm2KeyController(SmCryptoService cryptoService) {
        return new Sm2KeyController(cryptoService);
    }

    /**
     * 注册拦截器（只做 header 级校验，默认开启）
     */
    static class SmCryptoWebMvcConfigurer implements WebMvcConfigurer {

        private final SmCryptoInterceptor interceptor;

        SmCryptoWebMvcConfigurer(ObjectProvider<SmCryptoInterceptor> interceptorProvider) {
            this.interceptor = interceptorProvider.getIfAvailable();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            if (interceptor != null) {
                registry.addInterceptor(interceptor);
            }
        }
    }
}