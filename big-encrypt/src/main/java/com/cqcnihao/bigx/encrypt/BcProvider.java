package com.cqcnihao.bigx.encrypt;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;

/**
 * BouncyCastle Provider 注册器
 * <p>
 * SM2 / SM3 / SM4 均不在 JDK 内置算法列表中，必须依赖 BouncyCastle。
 * 这里做一次性、幂等的 Provider 注册，并对外暴露 {@link #provider()}，
 * 各算法工具类在静态初始化时引用它，确保「用到算法前 Provider 一定已注册」。
 *
 * @author bigweb
 */
@Slf4j
public final class BcProvider {

    /** JCA Provider 名称 */
    public static final String NAME = "BC";

    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();

    static {
        try {
            if (Security.getProvider(NAME) == null) {
                Security.addProvider(PROVIDER);
            }
        } catch (Exception e) {
            log.error("BouncyCastle Provider 自动注册失败，需手动调用 BcProvider.provider() 或检查 JVM 安全策略", e);
        }
    }

    private BcProvider() {
    }

    /**
     * 获取 BC Provider 实例（调用即触发注册）。
     * <p>
     * 注意：不要使用 {@link #NAME} 常量来触发初始化，它是编译期常量，
     * 不会触发本类的静态代码块执行。
     */
    public static Provider provider() {
        return PROVIDER;
    }
}