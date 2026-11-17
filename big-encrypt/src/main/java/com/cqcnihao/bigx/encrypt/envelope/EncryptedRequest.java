package com.cqcnihao.bigx.encrypt.envelope;

import lombok.Data;

import java.io.Serializable;

/**
 * 请求密文信封
 * <p>
 * 客户端请求体约定（Content-Type: application/json）：
 * <pre>
 * {
 *   "alg": "SM4/CBC/PKCS7Padding",
 *   "keyId": "v1",
 *   "cipher": "base64(iv+ciphertext)",
 *   "mac": "base64(hmac-sm3)",
 *   "sign": "base64(sm2 signature)",
 *   "timestamp": 1715083200000,
 *   "nonce": "8f14e45f-..."
 * }
 * </pre>
 * timestamp / nonce 既可以放 body，也可以放请求头
 * （{@code X-Sm-Timestamp} / {@code X-Sm-Nonce}），两者任一存在即可。
 *
 * @author bigweb
 */
@Data
public class EncryptedRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 对称算法变换名，为空则用服务端默认配置 */
    private String alg;

    /** 密钥版本，为空则用服务端默认 keyId */
    private String keyId;

    /** Base64(IV || 密文) */
    private String cipher;

    /** Base64(HMAC-SM3) */
    private String mac;

    /** Base64(SM2 签名) */
    private String sign;

    /** 客户端密钥标识（服务端登记时签发），为空则用服务端全局公钥验签 */
    private String clientKeyId;

    /** 客户端时间戳（毫秒） */
    private Long timestamp;

    /** 随机串，防重放 */
    private String nonce;

    public boolean hasCipher() {
        return cipher != null && !cipher.isBlank();
    }
}