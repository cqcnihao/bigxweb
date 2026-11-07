package com.cqcnihao.bigx.encrypt.service;

import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;

/**
 * 报文加解密门面
 * <p>
 * Advice 只依赖本接口，具体对称算法由 {@code big.encrypt.algorithm} 决定：
 * {@code SM4}（默认，国密合规）或 {@code AES}（与存量 AES 系统互通）。
 * SM2 签名 / SM3 防重放属于协议层，与对称算法选择无关，仍在接口内统一暴露。
 *
 * @author bigweb
 */
public interface MessageCryptoService {

    /**
     * 加密响应报文
     *
     * @param plaintextJson data 部分序列化后的 JSON 字符串
     * @param alg           期望算法，为空则取全局配置
     * @param keyId         期望密钥版本，为空则取全局默认
     */
    EncryptedPayload encrypt(String plaintextJson, String alg, String keyId);

    /**
     * 解密请求报文（含完整性校验）
     *
     * @param cipher Base64 密文
     * @param alg    算法
     * @param keyId  密钥版本
     * @param mac    客户端传入的 MAC（AES-GCM 自带认证时可为空）
     */
    String decrypt(String cipher, String alg, String keyId, String mac);

    /** 构造待签名串：timestamp + \n + [nonce + \n] + cipher */
    String signPayload(long timestamp, String nonce, String cipher);

    String signPayload(long timestamp, String cipher);

    /** SM2 私钥签名 */
    String sign(String raw);

    /** SM2 公钥验签 */
    boolean verify(String raw, String signBase64);

    /** 校验时间戳新鲜性与 nonce 防重放 */
    void checkFreshness(Long timestamp, String nonce);
}
