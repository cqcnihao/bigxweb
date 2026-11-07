package com.cqcnihao.bigx.common;

import java.io.Serializable;

/**
 * 统一响应结果接口
 * 定义所有响应必须遵循的标准
 *
 * @param <T> 数据类型
 * @author bigweb
 */
public interface IResult<T> extends Serializable {

    /**
     * 是否成功
     */
    boolean isSuccess();

    void setSuccess(boolean success);

    /**
     * 响应码
     */
    String getCode();

    void setCode(String code);

    /**
     * 响应消息
     */
    String getMessage();

    void setMessage(String message);

    /**
     * 响应数据
     */
    T getData();

    void setData(T data);

    /**
     * 时间戳
     */
    long getTimestamp();

    void setTimestamp(long timestamp);

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（无数据）
     */
    static <T> IResult<T> success() {
        Result<T> result = new Result<>();
        result.setTimestamp(System.currentTimeMillis());
        result.setSuccess(true);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        return result;
    }

    /**
     * 成功响应（带数据）
     */
    static <T> IResult<T> success(T data) {
        IResult<T> result = success();
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（带消息和数据）
     */
    static <T> IResult<T> success(String message, T data) {
        IResult<T> result = success(data);
        result.setMessage(message);
        return result;
    }

    /**
     * 失败响应（无消息）
     */
    static <T> IResult<T> error() {
        Result<T> result = new Result<>();
        result.setTimestamp(System.currentTimeMillis());
        result.setSuccess(false);
        result.setCode(ResultCode.FAIL.getCode());
        result.setMessage(ResultCode.FAIL.getMessage());
        return result;
    }

    /**
     * 失败响应（带消息）
     */
    static <T> IResult<T> error(String message) {
        IResult<T> result = error();
        result.setMessage(message);
        return result;
    }

    /**
     * 失败响应（带错误码和消息）
     */
    static <T> IResult<T> error(String code, String message) {
        IResult<T> result = error(message);
        result.setCode(code);
        return result;
    }

    /**
     * 失败响应（fail 作为 error 的别名）
     */
    static <T> IResult<T> fail() {
        return error();
    }

    static <T> IResult<T> fail(String message) {
        return error(message);
    }

    static <T> IResult<T> fail(String code, String message) {
        return error(code, message);
    }
}
