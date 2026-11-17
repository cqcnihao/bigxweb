package com.cqcnihao.bigx.encrypt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 国密加解密配置
 * <p>
 * 前缀 {@code big.encrypt}，示例：
 * <pre>
 * big:
 *   encrypt:
 *     enabled: true
 *     mode: ANNOTATION
 *     timestamp-validity: 300000
 *     replay-check: true
 *     skip-paths:
 *       - /actuator
 *       - /error
 *       - /doc.html
 *       - /api/auth/login
 *     sm4:
 *       alg: SM4/CBC/PKCS7Padding
 *       default-key-id: v1
 *       mac-enabled: true
 *       keys:
 *         v1: 3q2+7wAAAAAAAAAAAAAAAAAA==
 *     sm2:
 *       public-key: MFkwEw...
 *       private-key: MIGTAg...
 *       mode: C1C3C2
 *     sm3:
 *       mac-key: ...
 * </pre>
 *
 * @author bigweb
 */
@Data
@ConfigurationProperties(prefix = "big.encrypt")
public class EncryptProperties {

    /** 模块总开关 */
    private boolean enabled = true;

    /**
     * 生效模式
     * <ul>
     *   <li>{@link Mode#ANNOTATION}：只处理标注了 {@code @SmEncrypt} 的接口（推荐，默认）</li>
     *   <li>{@link Mode#GLOBAL}：除白名单外全部加解密</li>
     *   <li>{@link Mode#OFF}：完全关闭</li>
     * </ul>
     */
    private Mode mode = Mode.ANNOTATION;

    /** 时间戳有效期（毫秒），默认 5 分钟 */
    private long timestampValidity = 300000L;

    /** 是否开启 nonce 防重放 */
    private boolean replayCheck = true;

    /** 是否注册 HandlerInterceptor（只做 header 级校验，不读 body） */
    private boolean interceptorEnabled = true;

    /** 路径白名单（前缀匹配），命中即明文放行 */
    private String[] skipPaths = {
            "/actuator",
            "/error",
            "/doc.html",
            "/webjars",
            "/swagger-resources",
            "/v3/api-docs"
    };

    /** 响应体是否附带 SM2 签名，供客户端校验响应来源 */
    private boolean signResponse = false;

    /**
     * 对称算法选择
     * <ul>
     *   <li>{@link Algorithm#SM4}：国密（默认），配置项 {@code big.encrypt.sm4.*}</li>
     *   <li>{@link Algorithm#AES}：与存量 AES 系统互通，配置项 {@code big.encrypt.aes.*}</li>
     * </ul>
     * 非对称签名 / 防重放始终由 SM2 + SM3 承担，与对称算法选择无关。
     */
    private Algorithm algorithm = Algorithm.SM4;

    /**
     * 响应加密时是否跳过 {@code @Desensitize} 脱敏（默认 true）
     * <p>
     * data 序列化后会被整体加密，密文已保护数据；若仍先脱敏再加密，
     * 客户端解密后只能拿到脱敏值。关闭本项可恢复「脱敏 + 加密」的双重处理。
     */
    private boolean bypassDesensitize = true;

    public enum Mode {
        ANNOTATION,
        GLOBAL,
        OFF
    }

    public enum Algorithm {
        SM4,
        AES
    }

    /** AES 业务报文加密配置（仅 {@code algorithm=AES} 时生效） */
    @Data
    public static class AesConfig {
        /** 仅支持 AES-GCM（自带认证，无需额外 MAC） */
        private String alg = "AES/GCM/NoPadding";
        /** 默认密钥 ID */
        private String defaultKeyId = "default";
        /** keyId -> Base64 密钥（16/24/32 字节，推荐 32 字节即 AES-256） */
        private Map<String, String> keys = new LinkedHashMap<>();
    }

    /** SM4 业务报文加密配置 */
    @Data
    public static class Sm4Config {
        /** 变换名：SM4/CBC/PKCS7Padding 或 SM4/GCM/NoPadding */
        private String alg = com.cqcnihao.bigx.encrypt.core.Sm4.CBC_PKCS7;
        /** 默认密钥 ID */
        private String defaultKeyId = "default";
        /** 是否启用 Encrypt-then-MAC（HMAC-SM3），CBC 模式强烈建议开启 */
        private boolean macEnabled = true;
        /** keyId -> Base64 密钥（16 字节），支持多版本以便平滑轮换 */
        private Map<String, String> keys = new LinkedHashMap<>();
    }

    /** SM2 非对称配置：验签 + 会话密钥保护 */
    @Data
    public static class Sm2Config {
        /** Base64 X.509 公钥（验签 / 加密会话密钥用） */
        private String publicKey;
        /** Base64 PKCS#8 私钥（签名 / 解密会话密钥用） */
        private String privateKey;
        /** 密文排列：C1C3C2（新国标）或 C1C2C3 */
        private String mode = "C1C3C2";
        /**
         * 是否强制校验请求签名（默认开启，且为严格模式）
         * <p>
         * 开启后，加密信封<b>必须</b>携带 {@code sign}（或请求头 {@code X-Sign}），
         * 缺失或校验失败一律抛 {@code CODE_SIGN_INVALID}。签名依赖
         * {@code big.encrypt.sm2.public-key} 才能验，未配置公钥时所有加密请求都会被拒绝，
         * 因此启动期会做守卫：prod 环境缺公钥直接拒绝启动，其他环境打印 ERROR 强提醒。
         * <p>
         * 仅当确有无法签名的调用方（如纯浏览器前端且未下发私钥场景）需要放开时，
         * 才显式配置 {@code big.encrypt.sm2.verify-signature=false}。
         */
        private boolean verifySignature = true;
        /** 公钥版本号，随公钥一起下发给客户端，便于密钥轮换 */
        private String keyId = "v1";
        /** 是否暴露 SM2 公钥下发端点（混合加密握手第一步）；默认关闭，按需显式开启 */
        private boolean exposeKeyEndpoint = false;
        /** 公钥下发端点路径 */
        private String keyEndpointPath = "/crypto/sm2/public-key";
        /**
         * 是否允许客户端登记自己的 SM2 公钥（客户端密钥体系）
         * <p>
         * 浏览器无法持有服务端私钥，若要求「必须携带签名」，就必须让客户端自己生成
         * SM2 密钥对、把<b>公钥</b>登记到服务端，请求时用本地私钥签名、服务端按
         * {@code clientKeyId} 取对应公钥验签。开启后 {@link #verifySignature} 才能真正落地。
         * <p>
         * 注意：登记公钥只证明「请求方持有该私钥」，不等同于身份认证；
         * 身份仍由 Sa-Token 登录态承担，两者互补。
         */
        private boolean clientKeyEnabled = true;
        /** 客户端公钥登记端点路径 */
        private String clientKeyEndpointPath = "/crypto/sm2/client-key";
        /** 客户端公钥登记有效期（秒），默认 24 小时，过期需重新登记 */
        private long clientKeyTtlSeconds = 86400L;
        /** 单个客户端公钥最大长度（Base64 字符数），防止超大 payload 撑爆存储 */
        private int clientKeyMaxLength = 512;
    }

    /** SM3 配置 */
    @Data
    public static class Sm3Config {
        /** HMAC-SM3 的 MAC 密钥（Base64）；为空时回退为 SM4 密钥派生 */
        private String macKey;
    }

    private Sm4Config sm4 = new Sm4Config();
    private AesConfig aes = new AesConfig();
    private Sm2Config sm2 = new Sm2Config();
    private Sm3Config sm3 = new Sm3Config();
}