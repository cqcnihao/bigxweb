package com.cqcnihao.bigx.encrypt.core;

import com.cqcnihao.bigx.encrypt.BcProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;

/**
 * SM3 摘要算法（GB/T 32905-2016）
 * <p>
 * 在本模块中承担两种职责：
 * <ul>
 *   <li>摘要：{@link #digest(byte[])}，等价于 SHA-256 的位置，输出 256bit / 32 字节。</li>
 *   <li>完整性校验：{@link #hmac(byte[], byte[])}，采用 Encrypt-then-MAC 中的 HMAC-SM3。</li>
 * </ul>
 * HMAC-SM3 这里按 FIPS-198 标准构造手写实现（分组长度 64 字节），
 * 不依赖 BouncyCastle 的 Mac 别名注册，避免不同 BC 版本别名不一致导致 NoSuchAlgorithmException。
 *
 * @author bigweb
 */
public final class Sm3 {

    /** HMAC 分组长度（字节） */
    private static final int HMAC_BLOCK = 64;

    /** 摘要长度（字节） */
    public static final int DIGEST_LEN = 32;

    private static final Provider PROVIDER = BcProvider.provider();

    private Sm3() {
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SM3", PROVIDER);
        } catch (Exception e) {
            throw new IllegalStateException("SM3 不可用，请检查 BouncyCastle 依赖", e);
        }
    }

    /**
     * SM3 摘要
     *
     * @param data 原文
     * @return 32 字节摘要
     */
    public static byte[] digest(byte[] data) {
        return messageDigest().digest(data);
    }

    /**
     * SM3 摘要（字符串，UTF-8）
     */
    public static byte[] digest(String text) {
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SM3 摘要十六进制串
     */
    public static String digestHex(String text) {
        return toHex(digest(text));
    }

    /**
     * HMAC-SM3
     *
     * @param key  密钥
     * @param data 原文
     * @return 32 字节 MAC
     */
    public static byte[] hmac(byte[] key, byte[] data) {
        byte[] k = key;
        if (k.length > HMAC_BLOCK) {
            k = digest(k);
        }
        byte[] ipad = new byte[HMAC_BLOCK];
        byte[] opad = new byte[HMAC_BLOCK];
        for (int i = 0; i < HMAC_BLOCK; i++) {
            byte b = (i < k.length) ? k[i] : 0;
            ipad[i] = (byte) (b ^ 0x36);
            opad[i] = (byte) (b ^ 0x5c);
        }
        MessageDigest md = messageDigest();
        md.update(ipad);
        md.update(data);
        byte[] inner = md.digest();

        md = messageDigest();
        md.update(opad);
        md.update(inner);
        return md.digest();
    }

    /**
     * 常量时间比较，防止时序攻击
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= (a[i] ^ b[i]);
        }
        return diff == 0;
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(HEX[(b >> 4) & 0x0f]).append(HEX[b & 0x0f]);
        }
        return sb.toString();
    }
}
