package com.cqcnihao.bigx.common;

/**
 * 响应结果工具类
 */
public class ResultUtil {

    public static <T> Result<T> ok() {
        return Result.success();
    }

    public static <T> Result<T> ok(T data) {
        return Result.success(data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return Result.success(message, data);
    }

    public static <T> Result<T> fail() {
        return Result.error();
    }

    public static <T> Result<T> fail(String message) {
        return Result.error(message);
    }

    public static <T> Result<T> fail(String code, String message) {
        return Result.error(code, message);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return Result.error(resultCode.getCode(), resultCode.getMessage());
    }
}
