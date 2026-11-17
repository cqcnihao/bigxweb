package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import com.cqcnihao.bigx.encrypt.support.InMemoryClientKeyStore;
import com.cqcnihao.bigx.encrypt.support.InMemoryReplayGuard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 浏览器（sm-crypto）与服务端（BouncyCastle）的 SM2 签名互操作测试
 * <p>
 * 这是「必须携带签名」策略能否落地的命门：前端用 sm-crypto 生成密钥对并签名，
 * 服务端用 JCA + BC 验签。两侧任何一处差异（签名原文拼接、DER 编码、
 * userId 默认值、Z 值摘要）都会让所有加密请求 100% 被拒绝，且现象是
 * 「签名看起来完全正常但就是验不过」，极难排查。
 * <p>
 * 本用例的向量由前端 sm-crypto 真实生成（固定值，可回归）：
 * <pre>
 * const kp = sm2.generateKeyPairHex()
 * sm2.doSignature(payload, kp.privateKey, { der: true, hash: true })
 * </pre>
 *
 * @author bigweb
 */
class Sm2CrossLanguageTest {

    /** sm-crypto generateKeyPairHex() 产出的裸私钥（64 位 hex） */
    private static final String CLIENT_PRIVATE_KEY =
            "a3acbe7df72a684e122b29659987abceda40cbfae023407948d11501a755a43e";

    /** sm-crypto generateKeyPairHex() 产出的裸公钥点（04||X||Y，130 位 hex） */
    private static final String CLIENT_PUBLIC_KEY =
            "0426f9bf97e631a0ddbb8b44b4a71d112c55555f0d8906d5ac680d07212f8efed"
                    + "9aebfe8cbcdf5607c2d74bf9ca15f285c492af010c08d2d7adbb1b92eccf8df85";

    /** 前端签名原文：timestamp + "\n" + nonce + "\n" + cipher */
    private static final String SIGN_PAYLOAD = "1700000000000\nabcnonce\ncipherbase64==";

    /** 前端 doSignature(payload, priv, {der:true}) 输出的 DER 签名（Base64） */
    private static final String CLIENT_SIGN =
            "MEUCIAQN92hrd6ppDweeUnRVY9nDjg/s0frJ+0+js7KtHg+mAiEAm5iFNFY0t3qqGoYhzDk/9MfGamDUN/h9O3pVzc5V18o=";

    @Test
    void frontendRawPublicKeyShouldNormalizeAndVerifyFrontendSignature() {
        String normalized = Sm2.normalizeClientPublicKey(CLIENT_PUBLIC_KEY);
        assertTrue(Sm2.verify(SIGN_PAYLOAD.getBytes(StandardCharsets.UTF_8),
                        CLIENT_SIGN, normalized),
                "前端 sm-crypto 生成的签名必须能被服务端验签通过");
        assertFalse(Sm2.verify((SIGN_PAYLOAD + "x").getBytes(StandardCharsets.UTF_8),
                        CLIENT_SIGN, normalized),
                "签名原文被篡改后必须验签失败");
    }

    @Test
    void frontendSignatureShouldPassServiceVerifyByClientKeyId() {
        EncryptProperties props = new EncryptProperties();
        props.getSm2().setClientKeyEnabled(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L),
                new InMemoryClientKeyStore());

        String clientKeyId = service.registerClientKey(CLIENT_PUBLIC_KEY);
        assertTrue(service.verify(SIGN_PAYLOAD, CLIENT_SIGN, clientKeyId),
                "客户端登记公钥后，服务端应能按 clientKeyId 验签通过");
        assertEquals(32, clientKeyId.length());
    }
}