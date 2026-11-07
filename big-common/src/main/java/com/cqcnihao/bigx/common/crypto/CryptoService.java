package com.cqcnihao.bigx.common.crypto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 加密服务
 */
@Service
public class CryptoService {
    
    private final AesCryptoUtil aesCryptoUtil;
    
    @Autowired
    public CryptoService(CryptoProperties cryptoProperties) {
        if (cryptoProperties.isEnabled() && cryptoProperties.getSecretKey() != null) {
            this.aesCryptoUtil = new AesCryptoUtil(cryptoProperties.getSecretKey());
        } else {
            this.aesCryptoUtil = null;
        }
    }
    
    /**
     * 加密字符串
     */
    public String encrypt(String plainText) {
        if (aesCryptoUtil == null) {
            throw new IllegalStateException("AES加密未启用或密钥未配置");
        }
        return aesCryptoUtil.encrypt(plainText);
    }
    
    /**
     * 解密字符串
     */
    public String decrypt(String encryptedText) {
        if (aesCryptoUtil == null) {
            throw new IllegalStateException("AES加密未启用或密钥未配置");
        }
        return aesCryptoUtil.decrypt(encryptedText);
    }
}