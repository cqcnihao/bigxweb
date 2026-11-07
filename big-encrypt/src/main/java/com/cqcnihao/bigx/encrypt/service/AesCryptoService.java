package com.cqcnihao.bigx.encrypt.service;

import com.cqcnihao.bigx.common.crypto.AesCryptoUtil;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import com.cqcnihao.bigx.encrypt.support.ReplayGuard;

import java.util.Base64;
import java.util.Map;

/**
 * AES 报文加解密实现（复用 big-common 的 {@link AesCryptoUtil}，不另立一套算法）
 * <p>
 * 与 SM4 实现共用同一套协议：签名（SM2）、新鲜性 / 防重放（SM3 + nonce）、
 * 密钥多版本（keyId）都由 {@link SmCryptoService} 提供，本类只替换对称算法部分。
 * <p>
 * 选择 AES-GCM 的原因：GCM 是认证加密，密文自带完整性校验，
 * 因此响应信封不再附带 {@code mac} 字段（{@code mac} 为 null），
 * 客户端也无需再对密文做额外 MAC 校验。
 *
 * @author bigweb
 */
public class AesCryptoService extends SmCryptoService {

    private final EncryptProperties properties;

    public AesCryptoService(EncryptProperties properties, ReplayGuard replayGuard) {
        super(properties, replayGuard);
        this.properties = properties;
    }

    @Override
    public EncryptedPayload encrypt(String plaintextJson, String alg, String keyId) {
        String useAlg = resolveAlg(alg);
        String useKeyId = resolveKeyId(keyId);
        // 密钥解析放在 try 之外：配置缺失/非法要保留 CODE_KEY_MISSING，不能被当成加密失败
        String keyBase64 = resolveAesKey(useKeyId);
        String cipher;
        try {
            cipher = new AesCryptoUtil(keyBase64).encrypt(plaintextJson);
        } catch (RuntimeException e) {
            throw new CryptoException(CryptoException.CODE_ENCRYPT_FAILED, "AES 响应加密失败", e);
        }

        EncryptedPayload payload = new EncryptedPayload(useAlg, useKeyId, cipher, null);
        long now = System.currentTimeMillis();
        payload.setTimestamp(now);
        if (properties.isSignResponse()) {
            payload.setSign(sign(signPayload(now, cipher)));
        }
        return payload;
    }

    @Override
    public String decrypt(String cipher, String alg, String keyId, String mac) {
        // GCM 自行校验密文完整性：密文被篡改时 decrypt 抛异常，无需再比对 mac
        resolveAlg(alg);
        String useKeyId = resolveKeyId(keyId);
        String keyBase64 = resolveAesKey(useKeyId);
        try {
            return new AesCryptoUtil(keyBase64).decrypt(cipher);
        } catch (RuntimeException e) {
            throw new CryptoException(CryptoException.CODE_DECRYPT_FAILED, "AES 请求解密失败", e);
        }
    }

    /**
     * 只接受 AES-GCM：CBC 等模式没有内置认证，用它就得再补一套 MAC，
     * 与 SM4 的 Encrypt-then-MAC 重复，故不开放。
     */
    private String resolveAlg(String alg) {
        String useAlg = (alg == null || alg.isBlank()) ? properties.getAes().getAlg() : alg.trim();
        if (!AesCryptoUtil.ALGORITHM.equalsIgnoreCase(useAlg)) {
            throw new CryptoException(CryptoException.CODE_CONFIG,
                    "AES 模式仅支持 " + AesCryptoUtil.ALGORITHM + "，实际为 " + useAlg);
        }
        return useAlg;
    }

    private String resolveKeyId(String keyId) {
        return (keyId == null || keyId.isBlank())
                ? properties.getAes().getDefaultKeyId() : keyId.trim();
    }

    /**
     * 解析 AES 密钥（Base64），并校验长度：AES 只支持 16/24/32 字节
     */
    private String resolveAesKey(String keyId) {
        Map<String, String> keys = properties.getAes().getKeys();
        String base64 = keys == null ? null : keys.get(keyId);
        if (base64 == null || base64.isBlank()) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "未配置 AES 密钥：keyId=" + keyId + "（big.encrypt.aes.keys）");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "AES 密钥不是合法 Base64：keyId=" + keyId, e);
        }
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "AES 密钥长度必须为 16/24/32 字节，keyId=" + keyId + " 实际 " + key.length);
        }
        return base64.trim();
    }
}
