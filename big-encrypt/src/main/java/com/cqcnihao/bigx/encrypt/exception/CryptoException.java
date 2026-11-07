package com.cqcnihao.bigx.encrypt.exception;

import com.cqcnihao.bigx.core.exception.BusinessException;

/**
 * 国密加解密异常
 * <p>
 * 继承 {@link BusinessException}，因此会被 big-common 的
 * {@code GlobalExceptionHandler#handleBusinessException} 直接捕获并转成标准 Result，
 * 不会退化成 500 堆栈。
 *
 * @author bigweb
 */
public class CryptoException extends BusinessException {

    public static final String CODE_DECRYPT_FAILED = "CRY-4001";
    public static final String CODE_ENCRYPT_FAILED = "CRY-4002";
    public static final String CODE_SIGN_INVALID = "CRY-4003";
    public static final String CODE_MAC_INVALID = "CRY-4004";
    public static final String CODE_TIMESTAMP_EXPIRED = "CRY-4005";
    public static final String CODE_REPLAY = "CRY-4006";
    public static final String CODE_BAD_ENVELOPE = "CRY-4007";
    public static final String CODE_KEY_MISSING = "CRY-4008";
    public static final String CODE_CONFIG = "CRY-4009";

    public CryptoException(String code, String message) {
        super(code, message);
    }

    public CryptoException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
