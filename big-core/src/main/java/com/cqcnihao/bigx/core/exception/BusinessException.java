package com.cqcnihao.bigx.core.exception;

import lombok.Getter;

/**
 * 通用业务异常
 * 各模块可使用此异常统一抛出业务错误，由全局异常处理器捕获
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = "500";
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
