package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.core.Sm3;
import com.cqcnihao.bigx.encrypt.core.Sm4;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import com.cqcnihao.bigx.encrypt.support.InMemoryReplayGuard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 国密算法冒烟测试：验证 SM2 / SM3 / SM4 在 BouncyCastle 上确实可用且可互逆
 */
class SmCryptoSmokeTest {

    @Test
    void sm3_digestShouldBe32BytesAndStable() {
        byte[] d = Sm3.digest("abc");
        assertEquals(32, d.length);
        assertEquals(Sm3.digestHex("abc"), Sm3.digestHex("abc"));
        assertNotEquals(Sm3.digestHex("abc"), Sm3.digestHex("abd"));
    }

    @Test
    void sm3_hmacShouldBeDeterministicAndKeyDependent() {
        byte[] key = "secret-key".getBytes(StandardCharsets.UTF_8);
        byte[] a = Sm3.hmac(key, "hello".getBytes(StandardCharsets.UTF_8));
        byte[] b = Sm3.hmac(key, "hello".getBytes(StandardCharsets.UTF_8));
        byte[] c = Sm3.hmac("other-key".getBytes(StandardCharsets.UTF_8),
                "hello".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(a, b);
        assertEquals(32, a.length);
        assertFalse(Sm3.constantTimeEquals(a, c));
    }

    @Test
    void sm4_cbcRoundTrip() {
        byte[] key = Sm4.generateKey();
        assertEquals(Sm4.KEY_LEN, key.length);
        String cipher = Sm4.encrypt("{\"mobile\":\"13800000000\"}".getBytes(StandardCharsets.UTF_8),
                key, Sm4.CBC_PKCS7);
        byte[] plain = Sm4.decrypt(cipher, key, Sm4.CBC_PKCS7);
        assertEquals("{\"mobile\":\"13800000000\"}", new String(plain, StandardCharsets.UTF_8));
        // 每次加密 IV 随机，密文不应相同
        assertNotEquals(cipher, Sm4.encrypt("{\"mobile\":\"13800000000\"}".getBytes(StandardCharsets.UTF_8),
                key, Sm4.CBC_PKCS7));
    }

    @Test
    void sm4_gcmRoundTrip() {
        byte[] key = Sm4.generateKey();
        String cipher = Sm4.encrypt("国密SM4-GCM".getBytes(StandardCharsets.UTF_8), key, Sm4.GCM);
        byte[] plain = Sm4.decrypt(cipher, key, Sm4.GCM);
        assertEquals("国密SM4-GCM", new String(plain, StandardCharsets.UTF_8));
    }

    @Test
    void sm2_signAndVerify() {
        String[] kp = Sm2.generateKeyPairBase64();
        String pub = kp[0];
        String priv = kp[1];
        byte[] data = "timestamp=1&amount=100".getBytes(StandardCharsets.UTF_8);

        String sign = Sm2.sign(data, priv);
        assertTrue(Sm2.verify(data, sign, pub));
        assertFalse(Sm2.verify("被篡改的数据".getBytes(StandardCharsets.UTF_8), sign, pub));
    }

    @Test
    void sm2_encryptAndDecrypt() {
        String[] kp = Sm2.generateKeyPairBase64();
        for (Sm2.Mode mode : Sm2.Mode.values()) {
            String cipher = Sm2.encrypt("SM4会话密钥".getBytes(StandardCharsets.UTF_8), kp[0], mode);
            byte[] plain = Sm2.decrypt(cipher, kp[1], mode);
            assertEquals("SM4会话密钥", new String(plain, StandardCharsets.UTF_8),
                    "SM2 " + mode + " 加解密往返失败");
        }
    }

    @Test
    void service_encryptThenDecryptRoundTripWithMac() {
        EncryptProperties props = new EncryptProperties();
        props.getSm4().setAlg(Sm4.CBC_PKCS7);
        props.getSm4().setDefaultKeyId("v1");
        props.getSm4().setMacEnabled(true);
        props.getSm4().getKeys().put("v1", Sm4.generateKeyBase64());
        props.setTimestampValidity(300_000L);

        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L));

        String json = "{\"mobile\":\"13800000000\",\"idCard\":\"110101199001011234\"}";
        EncryptedPayload payload = service.encrypt(json, null, null);

        assertEquals("v1", payload.getKeyId());
        assertEquals(Sm4.CBC_PKCS7, payload.getAlg());
        assertTrue(payload.getMac() != null && !payload.getMac().isBlank());

        String plain = service.decrypt(payload.getCipher(), payload.getAlg(),
                payload.getKeyId(), payload.getMac());
        assertEquals(json, plain);
    }

    @Test
    void service_macTamperShouldBeRejected() {
        EncryptProperties props = new EncryptProperties();
        props.getSm4().setDefaultKeyId("v1");
        props.getSm4().getKeys().put("v1", Sm4.generateKeyBase64());
        props.getSm4().setMacEnabled(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L));

        EncryptedPayload payload = service.encrypt("{\"a\":1}", null, null);
        // 篡改密文后 MAC 校验必须失败
        String tampered = payload.getCipher().substring(0, payload.getCipher().length() - 4) + "AAAA";

        boolean rejected = false;
        try {
            service.decrypt(tampered, payload.getAlg(), payload.getKeyId(), payload.getMac());
        } catch (com.cqcnihao.bigx.encrypt.exception.CryptoException e) {
            rejected = true;
        }
        assertTrue(rejected, "密文被篡改后必须拒绝解密");
    }

    @Test
    void service_decryptSessionKeyShouldRestoreSm4Key() {
        String[] kp = Sm2.generateKeyPairBase64();
        EncryptProperties props = new EncryptProperties();
        props.getSm2().setPublicKey(kp[0]);
        props.getSm2().setPrivateKey(kp[1]);
        props.getSm2().setMode("C1C3C2");
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(60_000L));

        // 客户端：生成 16 字节会话密钥，用 SM2 公钥加密上送
        byte[] sessionKey = Sm4.generateKey();
        String encrypted = Sm2.encrypt(sessionKey, kp[0]);

        assertArrayEquals(sessionKey, service.decryptSessionKey(encrypted));
    }

    @Test
    void service_replayShouldBeRejected() {
        EncryptProperties props = new EncryptProperties();
        props.setReplayCheck(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L));

        service.checkFreshness(System.currentTimeMillis(), "nonce-001");

        boolean rejected = false;
        try {
            service.checkFreshness(System.currentTimeMillis(), "nonce-001");
        } catch (com.cqcnihao.bigx.encrypt.exception.CryptoException e) {
            rejected = true;
        }
        assertTrue(rejected, "重复 nonce 必须被拒绝");
    }

    @Test
    void clientKey_rawPointShouldNormalizeToX509AndVerifySignature() {
        String[] kp = Sm2.generateKeyPairBase64();
        String rawPoint = uncompressedPointHex(kp[0]);

        String normalized = Sm2.normalizeClientPublicKey(rawPoint);
        assertEquals(kp[0], normalized, "裸公钥点归一化后应与原始 X.509 完全一致");

        String sign = Sm2.sign("payload".getBytes(StandardCharsets.UTF_8), kp[1]);
        assertTrue(Sm2.verify("payload".getBytes(StandardCharsets.UTF_8), sign, normalized));
    }

    @Test
    void clientKey_registerThenVerifyByClientKeyId() {
        EncryptProperties props = new EncryptProperties();
        props.getSm2().setClientKeyEnabled(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L),
                new com.cqcnihao.bigx.encrypt.support.InMemoryClientKeyStore());

        String[] kp = Sm2.generateKeyPairBase64();
        String clientKeyId = service.registerClientKey(uncompressedPointHex(kp[0]));
        assertTrue(clientKeyId != null && clientKeyId.length() == 32,
                "clientKeyId 应为服务端随机签发的 128bit 标识");

        String payload = "1700000000000\nnonce\ncipher";
        String sign = Sm2.sign(payload.getBytes(StandardCharsets.UTF_8), kp[1]);
        assertTrue(service.verify(payload, sign, clientKeyId), "按登记的客户端公钥验签应通过");
        assertFalse(service.verify(payload, sign, "ffffffffffffffffffffffffffffffff"),
                "未登记的公钥必须验签失败，绝不允许降级放行");
        assertFalse(service.verify(payload, sign), "未配置服务端全局公钥时回退路径应失败");
    }

    @Test
    void clientKey_registerShouldRejectInvalidPoint() {
        EncryptProperties props = new EncryptProperties();
        props.getSm2().setClientKeyEnabled(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L),
                new com.cqcnihao.bigx.encrypt.support.InMemoryClientKeyStore());

        boolean rejected = false;
        try {
            service.registerClientKey("04" + "00".repeat(64));
        } catch (com.cqcnihao.bigx.encrypt.exception.CryptoException e) {
            rejected = true;
        }
        assertTrue(rejected, "不在 SM2 曲线上的公钥必须拒绝登记");
    }

    @Test
    void clientKey_ttlShouldBeExposedForClientRenewal() {
        EncryptProperties props = new EncryptProperties();
        props.getSm2().setClientKeyEnabled(true);
        props.getSm2().setClientKeyTtlSeconds(7200);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L),
                new com.cqcnihao.bigx.encrypt.support.InMemoryClientKeyStore());

        // 客户端必须能拿到有效期，否则公钥静默过期后所有加密请求都会被拒
        assertEquals(7200, service.getClientKeyTtlSeconds());
    }

    @Test
    void clientKey_shouldExpireAfterTtl() {
        EncryptProperties props = new EncryptProperties();
        props.getSm2().setClientKeyEnabled(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L),
                new com.cqcnihao.bigx.encrypt.support.InMemoryClientKeyStore());

        String[] kp = Sm2.generateKeyPairBase64();
        // TTL 设为 0 秒，写入即过期
        props.getSm2().setClientKeyTtlSeconds(0);
        String clientKeyId = service.registerClientKey(uncompressedPointHex(kp[0]));
        assertNotNull(clientKeyId);
        assertTrue(service.resolveClientPublicKey(clientKeyId) == null,
                "过期的客户端公钥必须查不到，避免旧密钥长期有效");
    }

    @Test
    void responseSign_whenEnabledShouldSignTimestampAndCipher() {
        String[] kp = Sm2.generateKeyPairBase64();
        EncryptProperties props = new EncryptProperties();
        props.getSm4().setDefaultKeyId("v1");
        props.getSm4().getKeys().put("v1", Sm4.generateKeyBase64());
        props.getSm2().setPublicKey(kp[0]);
        props.getSm2().setPrivateKey(kp[1]);
        props.setSignResponse(true);
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L));

        EncryptedPayload payload = service.encrypt("{\"a\":1}", null, null);
        assertNotNull(payload.getSign(), "开启响应签名后信封必须携带 sign");
        // 签名原文必须与前端约定一致：timestamp + "\n" + cipher，否则前端 100% 验不过
        String raw = payload.getTimestamp() + "\n" + payload.getCipher();
        assertTrue(Sm2.verify(raw.getBytes(StandardCharsets.UTF_8), payload.getSign(), kp[0]),
                "响应签名应能被对应公钥验签通过");
        assertFalse(Sm2.verify((payload.getTimestamp() + "\n" + payload.getCipher() + "x")
                        .getBytes(StandardCharsets.UTF_8), payload.getSign(), kp[0]),
                "密文被篡改后响应签名必须验签失败");
    }

    @Test
    void responseSign_whenDisabledShouldOmitSignField() {
        EncryptProperties props = new EncryptProperties();
        props.getSm4().setDefaultKeyId("v1");
        props.getSm4().getKeys().put("v1", Sm4.generateKeyBase64());
        SmCryptoService service = new SmCryptoService(props, new InMemoryReplayGuard(600_000L));

        EncryptedPayload payload = service.encrypt("{\"a\":1}", null, null);
        // 关闭时不能出现空 sign，否则前端会误判「有签名字段」而校验失败
        assertEquals(null, payload.getSign());
    }

    /** 从 X.509 Base64 提取 04||X||Y 的裸公钥点 hex，模拟浏览器 sm-crypto 的产出格式 */
    private static String uncompressedPointHex(String x509Base64) {
        try {
            java.security.PublicKey key = java.security.KeyFactory.getInstance("EC",
                            com.cqcnihao.bigx.encrypt.BcProvider.provider())
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(
                            java.util.Base64.getDecoder().decode(x509Base64)));
            org.bouncycastle.jce.interfaces.ECPublicKey ecKey =
                    (org.bouncycastle.jce.interfaces.ECPublicKey) key;
            byte[] encoded = ecKey.getQ().getEncoded(false);
            return java.util.HexFormat.of().formatHex(encoded);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}