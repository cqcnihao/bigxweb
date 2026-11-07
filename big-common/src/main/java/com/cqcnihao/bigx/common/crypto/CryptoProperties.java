package com.cqcnihao.bigx.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 加密配置属性
 */
@Component
@ConfigurationProperties(prefix = "crypto.aes")
public class CryptoProperties {
    
    /**
     * 是否启用AES加密
     */
    private boolean enabled = false;
    
    /**
     * AES密钥（Base64编码）
     */
    private String secretKey;
    
    /**
     * 加密算法，默认AES/GCM/NoPadding
     */
    private String algorithm = "AES/GCM/NoPadding";
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getSecretKey() {
        return secretKey;
    }
    
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    
    public String getAlgorithm() {
        return algorithm;
    }
    
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}