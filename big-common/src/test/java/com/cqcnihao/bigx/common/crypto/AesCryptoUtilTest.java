package com.cqcnihao.bigx.common.crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AES加密解密工具类测试
 */
public class AesCryptoUtilTest {
    
    @Test
    public void testEncryptDecrypt() {
        // 生成测试密钥
        String secretKey = AesCryptoUtil.generateKey();
        System.out.println("Generated Key: " + secretKey);
        
        // 创建加密工具
        AesCryptoUtil cryptoUtil = new AesCryptoUtil(secretKey);
        
        String original = "Hello, World! This is a test message.";
        
        // 加密
        String encrypted = cryptoUtil.encrypt(original);
        System.out.println("Encrypted: " + encrypted);
        
        // 解密
        String decrypted = cryptoUtil.decrypt(encrypted);
        System.out.println("Decrypted: " + decrypted);
        
        // 验证
        assertEquals(original, decrypted);
    }
    
    @Test
    public void testEmptyString() {
        String secretKey = AesCryptoUtil.generateKey();
        AesCryptoUtil cryptoUtil = new AesCryptoUtil(secretKey);
        
        String original = "";
        String encrypted = cryptoUtil.encrypt(original);
        String decrypted = cryptoUtil.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }
    
    @Test
    public void testSpecialCharacters() {
        String secretKey = AesCryptoUtil.generateKey();
        AesCryptoUtil cryptoUtil = new AesCryptoUtil(secretKey);
        
        String original = "测试中文!@#$%^&*()_+{}|:<>?";
        String encrypted = cryptoUtil.encrypt(original);
        String decrypted = cryptoUtil.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }
}