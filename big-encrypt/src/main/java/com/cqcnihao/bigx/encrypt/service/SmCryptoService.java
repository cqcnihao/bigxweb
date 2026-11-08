package com.cqcnihao.bigx.encrypt.service;

import com.cqcnihao.bigx.encrypt.client.CryptoConfigVO;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.core.Sm3;
import com.cqcnihao.bigx.encrypt.core.Sm4;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import com.cqcnihao.bigx.encrypt.support.ReplayGuard;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 国密加解密门面服务
 * <p>
 * 职责划分（对应文章里的「混合加密」思路）：
 * <ul>
 *   <li><b>SM4</b>：加密业务报文（快，对称，等价于 AES 的位置）。</li>
 *   <li><b>SM3</b>：Encrypt-then-MAC，先加密再算 HMAC-SM3，防止密文被篡改 / padding oracle。</li>
 *   <li><b>SM2</b>：只用于签名验签与保护 SM4 会话密钥（非对称，等价于 RSA 的位置），绝不直接加密大报文。</li>
 * </ul>
 *
 * @author bigweb
 */
public class SmCryptoService implements MessageCryptoService {

    /** Encrypt-then-MAC 的绑定串，固定盐值，避免与其它用途的 MAC 冲突 */
    private static final String MAC_BINDING = "big-encrypt-etm-v1";

    private final EncryptProperties properties;
    private final ReplayGuard replayGuard;

    public SmCryptoService(EncryptProperties properties, ReplayGuard replayGuard) {
        this.properties = properties;
        this.replayGuard = replayGuard;
    }

    // ==================== 响应加密 ====================

    /**
     * 加密响应报文
     *
     * @param plaintextJson data 部分序列化后的 JSON 字符串
     * @param alg           期望算法，为空则取全局配置
     * @param keyId         期望密钥版本，为空则取全局默认
     */
    public EncryptedPayload encrypt(String plaintextJson, String alg, String keyId) {
        String useAlg = (alg == null || alg.isBlank())
                ? properties.getSm4().getAlg() : alg;
        String useKeyId = (keyId == null || keyId.isBlank())
                ? properties.getSm4().getDefaultKeyId() : keyId;
        byte[] key = resolveKey(useKeyId);

        String cipher = Sm4.encrypt(plaintextJson.getBytes(StandardCharsets.UTF_8), key, useAlg);

        EncryptedPayload payload = new EncryptedPayload(useAlg, useKeyId, cipher, null);
        if (properties.getSm4().isMacEnabled()) {
            payload.setMac(mac(useAlg, useKeyId, cipher));
        }
        long now = System.currentTimeMillis();
        payload.setTimestamp(now);
        if (properties.isSignResponse()) {
            payload.setSign(sign(signPayload(now, cipher)));
        }
        return payload;
    }

    // ==================== 请求解密 ====================

    /**
     * 解密请求报文（含完整性校验）
     *
     * @param cipher Base64 密文
     * @param alg    算法
     * @param keyId  密钥版本
     * @param mac    客户端传入的 HMAC-SM3，为空时按配置决定是否强制校验
     */
    public String decrypt(String cipher, String alg, String keyId, String mac) {
        String useAlg = (alg == null || alg.isBlank())
                ? properties.getSm4().getAlg() : alg;
        String useKeyId = (keyId == null || keyId.isBlank())
                ? properties.getSm4().getDefaultKeyId() : keyId;
        byte[] key = resolveKey(useKeyId);

        // 先校验 MAC 再解密：Encrypt-then-MAC，避免把未认证的密文喂给解密器
        if (properties.getSm4().isMacEnabled()) {
            if (mac == null || mac.isBlank()) {
                throw new CryptoException(CryptoException.CODE_MAC_INVALID, "缺少完整性校验值 mac");
            }
            String expected = mac(useAlg, useKeyId, cipher);
            if (!Sm3.constantTimeEquals(expected.getBytes(StandardCharsets.UTF_8),
                    mac.getBytes(StandardCharsets.UTF_8))) {
                throw new CryptoException(CryptoException.CODE_MAC_INVALID, "报文完整性校验失败");
            }
        }

        return new String(Sm4.decrypt(cipher, key, useAlg), StandardCharsets.UTF_8);
    }

    // ==================== 签名 ====================

    /**
     * 构造待签名串：timestamp + \n + [nonce + \n] + cipher
     */
    public String signPayload(long timestamp, String nonce, String cipher) {
        return nonce == null || nonce.isBlank()
                ? timestamp + "\n" + cipher
                : timestamp + "\n" + nonce + "\n" + cipher;
    }

    public String signPayload(long timestamp, String cipher) {
        return timestamp + "\n" + cipher;
    }

    public String sign(String raw) {
        String privateKey = properties.getSm2().getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING, "未配置 big.encrypt.sm2.private-key");
        }
        return Sm2.sign(raw.getBytes(StandardCharsets.UTF_8), privateKey);
    }

    public boolean verify(String raw, String signBase64) {
        String publicKey = properties.getSm2().getPublicKey();
        if (publicKey == null || publicKey.isBlank() || signBase64 == null || signBase64.isBlank()) {
            return false;
        }
        return Sm2.verify(raw.getBytes(StandardCharsets.UTF_8), signBase64, publicKey);
    }

    // ==================== 新鲜性 / 防重放 ====================

    /**
     * 校验时间戳与 nonce
     *
     * @param timestamp 毫秒时间戳，为空则只校验 nonce
     * @param nonce     随机串，为空则只校验时间戳
     */
    public void checkFreshness(Long timestamp, String nonce) {
        if (timestamp != null) {
            long skew = Math.abs(System.currentTimeMillis() - timestamp);
            if (skew > properties.getTimestampValidity()) {
                throw new CryptoException(CryptoException.CODE_TIMESTAMP_EXPIRED,
                        "请求已过期，请校准客户端时间");
            }
        }
        if (properties.isReplayCheck() && !replayGuard.accept(nonce)) {
            throw new CryptoException(CryptoException.CODE_REPLAY, "重复请求已被拒绝");
        }
    }

    // ==================== 密钥 ====================

    public byte[] resolveKey(String keyId) {
        Map<String, String> keys = properties.getSm4().getKeys();
        String base64 = keys == null ? null : keys.get(keyId);
        if (base64 == null || base64.isBlank()) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "未配置 SM4 密钥：keyId=" + keyId + "（big.encrypt.sm4.keys）");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "SM4 密钥不是合法 Base64：keyId=" + keyId, e);
        }
        if (key.length != Sm4.KEY_LEN) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "SM4 密钥长度必须为 " + Sm4.KEY_LEN + " 字节，keyId=" + keyId + " 实际 " + key.length);
        }
        return key;
    }

    /**
     * 获取 MAC 密钥：优先用 big.encrypt.sm3.mac-key，未配置时由 SM4 密钥派生
     */
    private byte[] resolveMacKey(String keyId) {
        String macKey = properties.getSm3().getMacKey();
        if (macKey != null && !macKey.isBlank()) {
            try {
                return Base64.getDecoder().decode(macKey.trim());
            } catch (IllegalArgumentException e) {
                throw new CryptoException(CryptoException.CODE_KEY_MISSING, "SM3 mac-key 不是合法 Base64", e);
            }
        }
        byte[] raw = resolveKey(keyId);
        byte[] salt = Sm3.digest((MAC_BINDING + "|" + keyId).getBytes(StandardCharsets.UTF_8));
        return concat(salt, raw);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * 计算 Encrypt-then-MAC 值（Base64）
     */
    public String mac(String alg, String keyId, String cipher) {
        String data = alg + "|" + keyId + "|" + cipher;
        byte[] tag = Sm3.hmac(resolveMacKey(keyId), data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(tag);
    }

    /**
     * 用 SM2 私钥解密客户端上送的 SM4 会话密钥（混合加密握手第二步）
     * <p>
     * 这是 SM2 在本模块中<b>唯一推荐的加解密用法</b>：只处理 16 字节的会话密钥，
     * 业务报文一律交给 SM4。千万不要拿 SM2 直接加密业务报文——它比 SM4 慢几个数量级，
     * 且有明文长度上限。
     *
     * @param encryptedSessionKeyBase64 客户端用 {@link #sm2PublicKey()} 加密后的会话密钥（Base64）
     * @return SM4 会话密钥原始字节（16 字节）
     */
    public byte[] decryptSessionKey(String encryptedSessionKeyBase64) {
        String privateKey = properties.getSm2().getPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "未配置 big.encrypt.sm2.private-key，无法解密会话密钥");
        }
        Sm2.Mode mode = "C1C2C3".equalsIgnoreCase(properties.getSm2().getMode())
                ? Sm2.Mode.C1C2C3 : Sm2.Mode.C1C3C2;
        byte[] key = Sm2.decrypt(encryptedSessionKeyBase64, privateKey, mode);
        if (key.length != Sm4.KEY_LEN) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "会话密钥长度必须为 " + Sm4.KEY_LEN + " 字节，实际 " + key.length);
        }
        return key;
    }

    /**
     * SM2 公钥版本号（随公钥一起下发，用于密钥轮换）
     */
    public String sm2KeyId() {
        String keyId = properties.getSm2().getKeyId();
        return (keyId == null || keyId.isBlank()) ? "v1" : keyId.trim();
    }

    /**
     * SM2 公钥（下发给客户端用于加密会话密钥）
     */
    public String sm2PublicKey() {
        String pk = properties.getSm2().getPublicKey();
        if (pk == null || pk.isBlank()) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING, "未配置 big.encrypt.sm2.public-key");
        }
        return pk;
    }

    public EncryptProperties getProperties() {
        return properties;
    }

    public CryptoConfigVO toCryptoConfigVO() {
        CryptoConfigVO vo = new CryptoConfigVO();
        EncryptProperties.Sm4Config sm4 = properties.getSm4();
        EncryptProperties.Sm2Config sm2 = properties.getSm2();
        vo.setSm4Alg(sm4.getAlg());
        vo.setSm4DefaultKeyId(sm4.getDefaultKeyId());
        vo.setSm4Keys(sm4.getKeys());
        vo.setMacEnabled(sm4.isMacEnabled());
        vo.setMacBinding(MAC_BINDING);
        vo.setSm2PublicKey(sm2.getPublicKey());
        vo.setSm2KeyId(sm2.getKeyId());
        vo.setSm2Mode(sm2.getMode());
        return vo;
    }
}