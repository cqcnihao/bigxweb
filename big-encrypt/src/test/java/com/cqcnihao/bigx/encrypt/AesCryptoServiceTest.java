package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.common.crypto.AesCryptoUtil;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import com.cqcnihao.bigx.encrypt.service.AesCryptoService;
import com.cqcnihao.bigx.encrypt.support.InMemoryReplayGuard;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AES 对称实现：验证与 SM4 共用同一套协议（信封、keyId、签名 / 防重放）
 */
class AesCryptoServiceTest {

    private static EncryptProperties aesProps(String keyBase64) {
        EncryptProperties props = new EncryptProperties();
        props.setAlgorithm(EncryptProperties.Algorithm.AES);
        props.getAes().setDefaultKeyId("v1");
        props.getAes().getKeys().put("v1", keyBase64);
        return props;
    }

    @Test
    void aes_encryptThenDecryptRoundTrip() {
        AesCryptoService service = new AesCryptoService(aesProps(AesCryptoUtil.generateKey()),
                new InMemoryReplayGuard(600_000L));

        String json = "{\"mobile\":\"13800000000\",\"idCard\":\"110101199001011234\"}";
        EncryptedPayload payload = service.encrypt(json, null, null);

        // AES-GCM 自带认证，信封不再需要 mac
        assertEquals("AES/GCM/NoPadding", payload.getAlg());
        assertEquals("v1", payload.getKeyId());
        assertNull(payload.getMac());
        assertTrue(payload.getTimestamp() > 0);

        assertEquals(json, service.decrypt(payload.getCipher(), payload.getAlg(), payload.getKeyId(), null));
    }

    @Test
    void aes_tamperedCipherShouldBeRejected() {
        AesCryptoService service = new AesCryptoService(aesProps(AesCryptoUtil.generateKey()),
                new InMemoryReplayGuard(600_000L));
        EncryptedPayload payload = service.encrypt("{\"a\":1}", null, null);

        // 翻转密文最后一个字节，GCM tag 校验必须失败
        byte[] raw = Base64.getDecoder().decode(payload.getCipher());
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThrows(CryptoException.class,
                () -> service.decrypt(tampered, payload.getAlg(), payload.getKeyId(), null));
    }

    @Test
    void aes_unsupportedAlgShouldBeRejected() {
        AesCryptoService service = new AesCryptoService(aesProps(AesCryptoUtil.generateKey()),
                new InMemoryReplayGuard(600_000L));

        // 只允许 AES-GCM；CBC 没有内置认证，避免再补一套 MAC
        CryptoException e = assertThrows(CryptoException.class,
                () -> service.encrypt("{}", "AES/CBC/PKCS5Padding", null));
        assertEquals(CryptoException.CODE_CONFIG, e.getCode());
    }

    @Test
    void aes_invalidKeyLengthShouldBeRejected() {
        // 20 字节不是合法的 AES 密钥长度
        String badKey = Base64.getEncoder().encodeToString(new byte[20]);
        AesCryptoService service = new AesCryptoService(aesProps(badKey),
                new InMemoryReplayGuard(600_000L));

        CryptoException e = assertThrows(CryptoException.class,
                () -> service.encrypt("{}", null, null));
        assertEquals(CryptoException.CODE_KEY_MISSING, e.getCode());
    }
}
