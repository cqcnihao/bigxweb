package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.encrypt.advice.SmDecryptRequestBodyAdvice;
import com.cqcnihao.bigx.encrypt.advice.SmEncryptResponseBodyAdvice;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.controller.Sm2KeyController;
import com.cqcnihao.bigx.encrypt.interceptor.SmCryptoInterceptor;
import com.cqcnihao.bigx.encrypt.service.AesCryptoService;
import com.cqcnihao.bigx.encrypt.service.MessageCryptoService;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import com.cqcnihao.bigx.encrypt.controller.ClientKeyController;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.support.ClientKeyStore;
import com.cqcnihao.bigx.encrypt.support.InMemoryClientKeyStore;
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

import java.nio.charset.StandardCharsets;
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

    /**
     * 与各服务 yml 中 SM2 私钥的 dev 兜底默认值保持一致，仅限本地开发。
     * <p>
     * 一旦开启 {@code sign-response=true}，服务端会用这把私钥为每个加密响应签名；
     * 若生产环境沿用了这份公开在代码库里的 dev 私钥，任何人都能伪造响应签名，
     * 响应防篡改能力归零，因此 prod profile 下必须 fail-fast。
     */
    public static final String DEV_DEFAULT_SM2_PRIVATE_KEY =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQglEQ0TosYMTeLirfDIfeTwgehdBrAchz/jebTeFYpI9Kg"
                    + "CgYIKoEcz1UBgi2hRANCAAQYoirUSoBDmETs9p8nOo73YuIPMRgP2UBDgkDTCBn6O+w9r6NQ26Dsk/0ic+Q3to5IMrLgXXDGCWabeT96GhHL";

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
        checkProdMacKeyGuard();
        checkProdSm2Guard();
        checkProdSm2SignGuard();
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
        if (isProdProfile()) {
            throw new IllegalStateException("[SECURITY] 生产环境必须通过环境变量 SM4_KEY_V1 注入真实的 SM4 密钥，"
                    + "拒绝使用 dev 默认密钥或空密钥启动");
        }
        log.error("[SECURITY] 加密模块已启用但 SM4 密钥为空或 dev 默认值，仅限本地开发使用；"
                + "生产环境必须通过环境变量 SM4_KEY_V1（16 字节 Base64）覆盖");
    }

    /**
     * MAC 密钥与加密密钥必须分离：{@code sm3.mac-key} 为空时 MAC 密钥由 SM4 密钥派生，
     * 而 SM4 密钥经匿名端点 /auth/public-key 明文下发，HMAC 会退化为自证。
     * prod profile 下 {@code macEnabled=true} 且未注入独立 MAC 密钥时拒绝启动。
     */
    private void checkProdMacKeyGuard() {
        boolean macEnabled = Boolean.parseBoolean(
                environment.getProperty("big.encrypt.sm4.mac-enabled", "true"));
        String macKey = environment.getProperty("big.encrypt.sm3.mac-key", "");
        if (!macEnabled || !macKey.isBlank()) {
            return;
        }
        if (isProdProfile()) {
            throw new IllegalStateException("[SECURITY] 生产环境必须通过环境变量 SM3_MAC_KEY 注入独立的 MAC 密钥"
                    + "（32 字节 Base64 随机值），禁止由明文下发的 SM4 密钥派生");
        }
        log.error("[SECURITY] 未配置 big.encrypt.sm3.mac-key，MAC 密钥将由 SM4 密钥派生，"
                + "完整性校验退化为自证；生产环境必须通过 SM3_MAC_KEY 注入独立随机密钥");
    }

    /**
     * SM2 验签守卫：{@code verify-signature=true} 时加密信封<b>必须</b>携带合法签名，
     * 缺失或校验失败一律抛 {@code CODE_SIGN_INVALID}。验签的公钥来源有两条：
     * <ol>
     *   <li>客户端登记的密钥（{@code sm2.client-key-enabled=true}）：浏览器本地生成密钥对，
     *       公钥登记到服务端，请求带 {@code clientKeyId}，服务端据此验签——这是浏览器场景的正解；</li>
     *   <li>服务端全局公钥 {@code sm2.public-key}：服务端到服务端调用，或客户端直接持有服务端私钥的旧场景。</li>
     * </ol>
     * 两条来源都不可用时，所有加密接口会 100% 被拒绝，属于「配置遗漏 → 全站不可用」的硬故障，
     * 因此在启动期就 fail-fast。
     */
    private void checkProdSm2Guard() {
        boolean verifySignature = Boolean.parseBoolean(
                environment.getProperty("big.encrypt.sm2.verify-signature", "true"));
        if (!verifySignature) {
            if (isProdProfile()) {
                log.warn("[SECURITY] 生产环境已关闭 SM2 验签（big.encrypt.sm2.verify-signature=false），"
                        + "请求方身份不可抵赖性缺失");
            }
            return;
        }
        boolean clientKeyEnabled = Boolean.parseBoolean(
                environment.getProperty("big.encrypt.sm2.client-key-enabled", "true"));
        if (clientKeyEnabled) {
            log.info("SM2 验签公钥来源：客户端密钥体系（客户端登记公钥，请求携带 clientKeyId）");
            return;
        }
        String publicKey = environment.getProperty("big.encrypt.sm2.public-key", "");
        if (!publicKey.isBlank()) {
            return;
        }
        if (isProdProfile()) {
            throw new IllegalStateException("[SECURITY] 已开启 SM2 验签（verify-signature=true）但既关闭了客户端密钥体系"
                    + "（client-key-enabled=false），又未配置 big.encrypt.sm2.public-key，"
                    + "所有加密请求都会因无法验签被拒绝；请开启 client-key-enabled 或注入 SM2_PUBLIC_KEY");
        }
        log.error("[SECURITY] 已开启 SM2 验签但既关闭了客户端密钥体系又未配置 big.encrypt.sm2.public-key，"
                + "所有加密请求都会因无法验签被拒绝（CODE_SIGN_INVALID）；"
                + "请开启 big.encrypt.sm2.client-key-enabled 或注入 SM2_PUBLIC_KEY");
    }

    /**
     * 响应签名守卫：{@code sign-response=true} 时每个加密响应都会用 {@code sm2.private-key} 签名，
     * 私钥为空/非法会让所有加密接口在返回阶段抛 {@code CODE_KEY_MISSING}，直接 500。
     * <p>
     * 这是「配置遗漏 → 全站不可用」的硬故障，且发生在响应阶段、业务逻辑已执行完毕，
     * 排查成本很高，因此必须在启动期就 fail-fast（不区分 profile，任何环境都不允许带着必炸配置启动）。
     * <p>
     * 另外，dev 兜底私钥已公开在代码库中，生产沿用它等于把响应签名能力交给所有人，
     * prod profile 下同样拒绝启动。
     */
    private void checkProdSm2SignGuard() {
        boolean signResponse = Boolean.parseBoolean(
                environment.getProperty("big.encrypt.sign-response", "false"));
        if (!signResponse) {
            return;
        }
        String privateKey = environment.getProperty("big.encrypt.sm2.private-key", "");
        if (privateKey.isBlank()) {
            throw new IllegalStateException("[SECURITY] 已开启响应签名（big.encrypt.sign-response=true）"
                    + "但未配置 big.encrypt.sm2.private-key，所有加密响应都会因签名失败返回 500；"
                    + "请注入 SM2_PRIVATE_KEY 或关闭 sign-response");
        }
        if (DEV_DEFAULT_SM2_PRIVATE_KEY.equals(privateKey.trim())) {
            if (isProdProfile()) {
                throw new IllegalStateException("[SECURITY] 生产环境禁止使用代码库中的 dev 默认 SM2 私钥签名响应，"
                        + "该私钥已公开，任何人都能伪造响应签名；请通过环境变量 SM2_PRIVATE_KEY 注入真实私钥");
            }
            log.error("[SECURITY] 正在使用代码库中的 dev 默认 SM2 私钥签名响应，仅限本地开发；"
                    + "生产环境必须通过环境变量 SM2_PRIVATE_KEY 注入真实私钥");
        }
        // 空值/默认值之外，私钥还可能「格式非法、曲线不匹配」——这类问题同样只会在响应阶段爆出 500，
        // 这里直接拿真实私钥签一次样例报文，签不出来就拒绝启动，把故障从运行期前移到启动期
        try {
            Sm2.sign("big-encrypt-sign-probe".getBytes(StandardCharsets.UTF_8), privateKey.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("[SECURITY] big.encrypt.sm2.private-key 无法用于响应签名（"
                    + e.getMessage() + "），所有加密响应都会返回 500；请检查 SM2_PRIVATE_KEY 是否为合法的 "
                    + "PKCS#8 Base64 SM2 私钥", e);
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
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
    public SmCryptoService smCryptoService(EncryptProperties properties, ReplayGuard replayGuard,
                                          ObjectProvider<ClientKeyStore> clientKeyStore) {
        ClientKeyStore store = clientKeyStore.getIfAvailable();
        if (store == null) {
            store = inMemoryClientKeyStore();
        }
        if (properties.getAlgorithm() == EncryptProperties.Algorithm.AES) {
            log.info("big-encrypt 对称算法：AES-GCM（big.encrypt.algorithm=AES）");
            return new AesCryptoService(properties, replayGuard, store);
        }
        return new SmCryptoService(properties, replayGuard, store);
    }

    private ClientKeyStore inMemoryClientKeyStore() {
        log.warn("未检测到可用的 Redis，客户端 SM2 公钥回退为 JVM 内存存储 —— 仅单实例部署有效。"
                + "多实例/集群环境请引入 spring-boot-starter-data-redis，否则会出现随机性验签失败。");
        return new InMemoryClientKeyStore();
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
            havingValue = "true", matchIfMissing = false)
    public Sm2KeyController sm2KeyController(SmCryptoService cryptoService) {
        return new Sm2KeyController(cryptoService);
    }

    /**
     * 客户端 SM2 公钥登记端点
     * <p>
     * 与 {@link Sm2KeyController} 同范式：{@code final} + 类级 {@code @ConditionalOnExpression}，
     * 保证组件扫描不会在 {@code client-key-enabled=false} 时注册它。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "big.encrypt.sm2", name = "client-key-enabled",
            havingValue = "true", matchIfMissing = true)
    public ClientKeyController clientKeyController(SmCryptoService cryptoService) {
        return new ClientKeyController(cryptoService);
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