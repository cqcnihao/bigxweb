package com.cqcnihao.bigx.encrypt.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 响应密文信封
 * <p>
 * 约定：只加密 {@code Result.data} 部分，保留 {@code success/code/message} 明文，
 * 这样前端无需解密即可判断业务成败（对应文章里的「方案 B」）。
 * <p>
 * 最终响应形态：
 * <pre>
 * {
 *   "success": true,
 *   "code": "200",
 *   "message": "success",
 *   "timestamp": 1715083200000,
 *   "data": {
 *     "encrypt": true,
 *     "alg": "SM4/CBC/PKCS7Padding",
 *     "keyId": "v1",
 *     "cipher": "base64(iv+ciphertext)",
 *     "mac": "base64(hmac-sm3)",
 *     "sign": "base64(sm2 signature)"   // 仅 sign-response=true 时存在
 *   }
 * }
 * </pre>
 *
 * @author bigweb
 */
@Data
public class EncryptedPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 恒为 true，前端据此判断是否需要解密 */
    private Boolean encrypt = Boolean.TRUE;

    /** 对称算法变换名 */
    private String alg;

    /** 密钥版本，支持轮换期多版本共存 */
    private String keyId;

    /** Base64(IV || 密文) */
    private String cipher;

    /** Base64(HMAC-SM3)，Encrypt-then-MAC */
    private String mac;

    /** Base64(SM2 签名)，可选；未签名时直接省略该字段，避免前端误判“存在签名字段” */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String sign;

    /** 服务端时间戳，供客户端做新鲜性校验 */
    private Long timestamp;

    public EncryptedPayload() {
    }

    public EncryptedPayload(String alg, String keyId, String cipher, String mac) {
        this.alg = alg;
        this.keyId = keyId;
        this.cipher = cipher;
        this.mac = mac;
    }
}