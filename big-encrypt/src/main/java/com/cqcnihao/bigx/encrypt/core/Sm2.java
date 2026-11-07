package com.cqcnihao.bigx.encrypt.core;

import com.cqcnihao.bigx.encrypt.BcProvider;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * SM2 椭圆曲线公钥密码算法（GB/T 32918-2016）
 * <p>
 * 在本模块中承担两种职责：
 * <ul>
 *   <li>数字签名：{@link #sign(byte[], String)} / {@link #verify(byte[], String, String)}，
 *       内部使用 SM3withSM2（SM2 签名规范中的摘要固定为 SM3）。</li>
 *   <li>密钥传输：{@link #encrypt(byte[], String, Mode)} / {@link #decrypt(String, String, Mode)}，
 *       对应 RSA 在「SM2 + SM4 混合加密」里的角色——只加密短小的 SM4 会话密钥，不加密业务报文。</li>
 * </ul>
 * <p>
 * 密文排列：国标 GB/T 35276-2017 使用 C1C3C2，前端国密库（sm-crypto / gm-crypto）默认同样是
 * C1C3C2；老版本国密库用 C1C2C3。这里显式传入 {@link Mode}，默认 {@link Mode#C1C3C2}。
 *
 * @author bigweb
 */
public final class Sm2 {

    /** 国密推荐曲线 */
    public static final String CURVE = "sm2p256v1";

    /** SM2 签名算法（摘要固定 SM3） */
    public static final String SIGN_ALG = "SM3withSM2";

    /** 密文排列方式 */
    public enum Mode {
        /** GB/T 35276-2017 新国标，前端 sm-crypto 默认 */
        C1C3C2,
        /** 旧标准 / 部分老国密库 */
        C1C2C3;

        SM2Engine.Mode toBcMode() {
            return this == C1C3C2 ? SM2Engine.Mode.C1C3C2 : SM2Engine.Mode.C1C2C3;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Provider PROVIDER = BcProvider.provider();

    private Sm2() {
    }

    // ==================== 密钥 ====================

    /**
     * 生成 SM2 密钥对（Base64 编码的 X.509 公钥 / PKCS#8 私钥）
     *
     * @return [0]=公钥Base64, [1]=私钥Base64
     */
    public static String[] generateKeyPairBase64() {
        KeyPair kp = generateKeyPair();
        return new String[]{
                Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())
        };
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC", PROVIDER);
            g.initialize(new ECNamedCurveGenParameterSpec(CURVE), RANDOM);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_CONFIG, "SM2 密钥对生成失败", e);
        }
    }

    private static PublicKey toPublicKey(String base64) {
        try {
            return KeyFactory.getInstance("EC", PROVIDER)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING, "SM2 公钥解析失败", e);
        }
    }

    private static PrivateKey toPrivateKey(String base64) {
        try {
            return KeyFactory.getInstance("EC", PROVIDER)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING, "SM2 私钥解析失败", e);
        }
    }

    // ==================== 加解密（仅用于短数据，如会话密钥） ====================

    /**
     * SM2 公钥加密（默认 C1C3C2）
     */
    public static String encrypt(byte[] data, String publicKeyBase64) {
        return encrypt(data, publicKeyBase64, Mode.C1C3C2);
    }

    public static String encrypt(byte[] data, String publicKeyBase64, Mode mode) {
        try {
            SM2Engine engine = new SM2Engine(mode.toBcMode());
            ECPublicKeyParameters params =
                    (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(toPublicKey(publicKeyBase64));
            engine.init(true, new ParametersWithRandom(params, RANDOM));
            return Base64.getEncoder().encodeToString(engine.processBlock(data, 0, data.length));
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_ENCRYPT_FAILED, "SM2 加密失败", e);
        }
    }

    /**
     * SM2 私钥解密（默认 C1C3C2）
     */
    public static byte[] decrypt(String cipherBase64, String privateKeyBase64) {
        return decrypt(cipherBase64, privateKeyBase64, Mode.C1C3C2);
    }

    public static byte[] decrypt(String cipherBase64, String privateKeyBase64, Mode mode) {
        try {
            byte[] cipher = Base64.getDecoder().decode(cipherBase64);
            SM2Engine engine = new SM2Engine(mode.toBcMode());
            ECPrivateKeyParameters params =
                    (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(toPrivateKey(privateKeyBase64));
            engine.init(false, params);
            return engine.processBlock(cipher, 0, cipher.length);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_DECRYPT_FAILED, "SM2 解密失败", e);
        }
    }

    // ==================== 签名 / 验签 ====================

    /**
     * SM3withSM2 签名
     *
     * @return Base64 签名值
     */
    public static String sign(byte[] data, String privateKeyBase64) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALG, PROVIDER);
            sig.initSign(toPrivateKey(privateKeyBase64));
            sig.update(data);
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_SIGN_INVALID, "SM2 签名失败", e);
        }
    }

    /**
     * SM3withSM2 验签
     */
    public static boolean verify(byte[] data, String signBase64, String publicKeyBase64) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALG, PROVIDER);
            sig.initVerify(toPublicKey(publicKeyBase64));
            sig.update(data);
            return sig.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            return false;
        }
    }
}
