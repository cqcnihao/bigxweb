package com.cqcnihao.bigx.encrypt.client;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 面向前端的加密配置快照
 * <p>
 * 后端 /auth/public-key（或专用端点）下发，供前端动态装载 SM4 密钥、算法参数、SM2 公钥等。
 * <p>
 * 故意<b>不下发</b>以下敏感字段：
 * <ul>
 *   <li>{@code sm3.mac-key}：HMAC-SM3 的独立密钥，下发等于放弃 Encrypt-then-MAC 的全部安全意义</li>
 *   <li>{@code sm2.private-key}：绝不下发</li>
 * </ul>
 * MAC key 若配置为从 SM4 key 派生，前端可自行按同样规则派生。
 *
 * @author bigweb
 */
@Data
public class CryptoConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SM4 变换名，例：SM4/CBC/PKCS7Padding */
    private String sm4Alg;

    /** 当前默认生效的 keyId（密钥轮换时前端可按信封里带的 keyId 选择历史 key） */
    private String sm4DefaultKeyId;

    /** 所有可用的 SM4 密钥：keyId → Base64(rawKey) */
    private Map<String, String> sm4Keys;

    /** 是否开启 Encrypt-then-MAC（前端用于决定是否要算 MAC） */
    private boolean macEnabled;

    /** MAC 绑定串，固定值，前端必须与此完全一致才能算对 MAC */
    private String macBinding;

    /** SM2 X.509 公钥（Base64），用于验签或保护会话密钥 */
    private String sm2PublicKey;

    /** SM2 公钥版本号（用于密钥轮换） */
    private String sm2KeyId;

    /** SM2 密文模式：C1C3C2 或 C1C2C3 */
    private String sm2Mode;
}