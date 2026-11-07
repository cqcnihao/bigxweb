package com.cqcnihao.bigx.encrypt.core;

import com.cqcnihao.bigx.encrypt.BcProvider;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SM4 分组对称加密（GB/T 32907-2016）
 * <p>
 * 定位：国密体系里的「AES」，负责业务报文的加解密。SM2 只用来保护 SM4 的会话密钥。
 * <p>
 * 密文格式：Base64( IV || ciphertext[|| GCM tag] )，IV 随每次加密随机生成并前置。
 * <ul>
 *   <li>CBC 模式：IV 16 字节，PKCS7Padding，配合外部 HMAC-SM3 做 Encrypt-then-MAC。</li>
 *   <li>GCM 模式：IV 12 字节，128bit Tag 由 BC 自动追加到密文尾部，自带认证。</li>
 * </ul>
 *
 * @author bigweb
 */
public final class Sm4 {

    /** 默认（也是最兼容）的 SM4 变换 */
    public static final String CBC_PKCS7 = "SM4/CBC/PKCS7Padding";

    /** 自带认证的 SM4 变换 */
    public static final String GCM = "SM4/GCM/NoPadding";

    /** SM4 密钥长度：16 字节 / 128bit */
    public static final int KEY_LEN = 16;

    private static final int CBC_IV_LEN = 16;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BIT = 128;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile Provider cachedProvider;

    private Sm4() {
    }

    private static Provider provider() {
        if (cachedProvider == null) {
            try {
                cachedProvider = BcProvider.provider();
            } catch (Exception e) {
                throw new CryptoException(CryptoException.CODE_ENCRYPT_FAILED,
                        "BouncyCastle Provider 不可用，请确认 BC 依赖是否存在且注册成功", e);
            }
        }
        return cachedProvider;
    }

    /**
     * 生成 SM4 密钥（原始字节）
     */
    public static byte[] generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("SM4", provider());
            kg.init(KEY_LEN * 8);
            SecretKey sk = kg.generateKey();
            byte[] raw = sk.getEncoded();
            if (raw != null && raw.length == KEY_LEN) {
                return raw;
            }
            byte[] fallback = new byte[KEY_LEN];
            RANDOM.nextBytes(fallback);
            return fallback;
        } catch (Exception e) {
            // 部分 JDK/BC 组合下 SM4 KeyGenerator 可能未注册，退化为安全随机
            byte[] fallback = new byte[KEY_LEN];
            RANDOM.nextBytes(fallback);
            return fallback;
        }
    }

    /**
     * 生成 SM4 密钥（Base64，可直接写进 yml）
     */
    public static String generateKeyBase64() {
        return Base64.getEncoder().encodeToString(generateKey());
    }

    /**
     * SM4 加密
     *
     * @param plain         明文
     * @param key           16 字节密钥
     * @param transformation 变换名，见 {@link #CBC_PKCS7} / {@link #GCM}
     * @return Base64(IV || 密文)
     */
    public static String encrypt(byte[] plain, byte[] key, String transformation) {
        try {
            Cipher cipher = Cipher.getInstance(transformation, provider());
            byte[] iv = newIv(transformation);
            cipher.init(Cipher.ENCRYPT_MODE, toKey(key), spec(iv, transformation));
            byte[] ct = cipher.doFinal(plain);

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_ENCRYPT_FAILED, "SM4 加密失败", e);
        }
    }

    /**
     * SM4 解密
     *
     * @param cipherBase64 Base64(IV || 密文)
     * @param key          16 字节密钥
     * @param transformation 变换名
     * @return 明文字节
     */
    public static byte[] decrypt(String cipherBase64, byte[] key, String transformation) {
        try {
            byte[] raw = Base64.getDecoder().decode(cipherBase64);
            int ivLen = ivLen(transformation);
            if (raw.length <= ivLen) {
                throw new CryptoException(CryptoException.CODE_BAD_ENVELOPE, "SM4 密文长度非法");
            }
            byte[] iv = new byte[ivLen];
            byte[] ct = new byte[raw.length - ivLen];
            System.arraycopy(raw, 0, iv, 0, ivLen);
            System.arraycopy(raw, ivLen, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(transformation, provider());
            cipher.init(Cipher.DECRYPT_MODE, toKey(key), spec(iv, transformation));
            return cipher.doFinal(ct);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_DECRYPT_FAILED, "SM4 解密失败，请检查密钥或密文是否被篡改", e);
        }
    }

    private static SecretKey toKey(byte[] key) {
        if (key == null || key.length != KEY_LEN) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING,
                    "SM4 密钥长度必须为 " + KEY_LEN + " 字节，实际为 " + (key == null ? "null" : key.length));
        }
        return new SecretKeySpec(key, "SM4");
    }

    private static boolean isGcm(String transformation) {
        return transformation != null && transformation.toUpperCase().contains("GCM");
    }

    private static int ivLen(String transformation) {
        return isGcm(transformation) ? GCM_IV_LEN : CBC_IV_LEN;
    }

    private static byte[] newIv(String transformation) {
        byte[] iv = new byte[ivLen(transformation)];
        RANDOM.nextBytes(iv);
        return iv;
    }

    private static java.security.spec.AlgorithmParameterSpec spec(byte[] iv, String transformation) {
        return isGcm(transformation)
                ? new GCMParameterSpec(GCM_TAG_BIT, iv)
                : new IvParameterSpec(iv);
    }
}