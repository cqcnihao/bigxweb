package com.cqcnihao.bigx.utils.response;

import com.cqcnihao.bigx.common.IResult;
import com.cqcnihao.bigx.common.ResultCode;

/**
 * 统一 API 响应封装（Record 版）
 * <p>
 * 基于 Java Record 的不可变响应实现，提供与 {@link IResult} 接口兼容的静态工厂方法。
 * 相比可变的 {@link com.cqcnihao.bigx.common.Result} 类，本类具有线程安全性和简洁性的优势。
 * </p>
 *
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        long timestamp
) implements IResult<T> {

    // ==================== 成功响应 ====================

    /**
     * 成功响应（带数据）
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, System.currentTimeMillis());
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * 成功响应（带自定义消息和数据）
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, ResultCode.SUCCESS.getCode(), message, data, System.currentTimeMillis());
    }

    // ==================== 失败响应 ====================

    /**
     * 失败响应（使用默认错误码和消息）
     */
    public static <T> ApiResponse<T> fail() {
        return new ApiResponse<>(false, ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * 失败响应（带自定义消息）
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, ResultCode.FAIL.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 失败响应（带自定义错误码和消息）
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, System.currentTimeMillis());
    }

    // ==================== error 作为 fail 的别名 ====================

    public static <T> ApiResponse<T> error() {
        return fail();
    }

    public static <T> ApiResponse<T> error(String message) {
        return fail(message);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return fail(code, message);
    }

    // ==================== IResult 接口实现 ====================
    // Record 组件生成的访问器名为 success()/code()/message()/data()/timestamp()
    // 与 IResult 接口的 get* / is* 命名不一致，需显式覆盖 getter

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public T getData() {
        return data;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public void setSuccess(boolean success) {
        throw new UnsupportedOperationException("ApiResponse is immutable");
    }

    @Override
    public void setCode(String code) {
        throw new UnsupportedOperationException("ApiResponse is immutable");
    }

    @Override
    public void setMessage(String message) {
        throw new UnsupportedOperationException("ApiResponse is immutable");
    }

    @Override
    public void setData(T data) {
        throw new UnsupportedOperationException("ApiResponse is immutable");
    }

    @Override
    public void setTimestamp(long timestamp) {
        throw new UnsupportedOperationException("ApiResponse is immutable");
    }
}