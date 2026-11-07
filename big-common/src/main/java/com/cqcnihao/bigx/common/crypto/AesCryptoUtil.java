package com.cqcnihao.bigx.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加密解密工具类
 * 使用AES-256-GCM算法，提供认证加密
 */
public class AesCryptoUtil {
    
    /** AES-GCM 变换名（认证加密，密文自带完整性校验） */
    public static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    
    private final SecretKey secretKey;
    
    public AesCryptoUtil(String secretKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }
    
    /**
     * 加密字符串
     */
    public String encrypt(String plainText) {
        try {
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // 将IV和加密数据合并
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            
            return Base64.getEncoder().encodeToString(result);
            
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    /**
     * 解密字符串
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            
            // 提取IV和加密数据
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[decoded.length - GCM_IV_LENGTH];
            
            System.arraycopy(decoded, 0, iv, 0, iv.length);
            System.arraycopy(decoded, iv.length, encryptedData, 0, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            
            byte[] decrypted = cipher.doFinal(encryptedData);
            return new String(decrypted, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
    
    /**
     * 生成AES密钥（Base64编码）
     */
    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(KEY_SIZE);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("生成密钥失败", e);
        }
    }
}