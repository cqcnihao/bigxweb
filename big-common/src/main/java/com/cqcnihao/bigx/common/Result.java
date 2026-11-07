package com.cqcnihao.bigx.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果实现类
 * 实现 IResult 接口，提供标准的响应格式
 *
 * @param <T> 数据类型
 * @author bigweb
 */
@Data
@NoArgsConstructor
public class Result<T> implements IResult<T> {
    
    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;
    
    /** 响应码 */
    private String code;
    
    /** 响应消息 */
    private String message;
    
    /** 响应数据 */
    private T data;
    
    /** 时间戳 */
    private long timestamp;

    // ==================== 兼容旧版静态方法 ====================
    // 这些方法委托给 IResult 接口的静态方法，保持向后兼容

    /**
     * 成功响应（无数据）
     * @deprecated 使用 IResult.success() 替代
     */
    @Deprecated
    public static <T> Result<T> success() {
        return (Result<T>) IResult.success();
    }

    /**
     * 成功响应（带数据）
     * @deprecated 使用 IResult.success(data) 替代
     */
    @Deprecated
    public static <T> Result<T> success(T data) {
        return (Result<T>) IResult.success(data);
    }

    /**
     * 成功响应（带消息和数据）
     * @deprecated 使用 IResult.success(message, data) 替代
     */
    @Deprecated
    public static <T> Result<T> success(String message, T data) {
        return (Result<T>) IResult.success(message, data);
    }

    /**
     * 失败响应（无消息）
     * @deprecated 使用 IResult.error() 替代
     */
    @Deprecated
    public static <T> Result<T> error() {
        return (Result<T>) IResult.error();
    }

    /**
     * 失败响应（带消息）
     * @deprecated 使用 IResult.error(message) 替代
     */
    @Deprecated
    public static <T> Result<T> error(String message) {
        return (Result<T>) IResult.error(message);
    }

    /**
     * 失败响应（带错误码和消息）
     * @deprecated 使用 IResult.error(code, message) 替代
     */
    @Deprecated
    public static <T> Result<T> error(String code, String message) {
        return (Result<T>) IResult.error(code, message);
    }

    /**
     * 失败响应（fail 方法）
     * @deprecated 使用 IResult.fail() 替代
     */
    @Deprecated
    public static <T> Result<T> fail() {
        return (Result<T>) IResult.fail();
    }

    /**
     * 失败响应（带消息）
     * @deprecated 使用 IResult.fail(message) 替代
     */
    @Deprecated
    public static <T> Result<T> fail(String message) {
        return (Result<T>) IResult.fail(message);
    }

    /**
     * 失败响应（带错误码和消息）
     * @deprecated 使用 IResult.fail(code, message) 替代
     */
    @Deprecated
    public static <T> Result<T> fail(String code, String message) {
        return (Result<T>) IResult.fail(code, message);
    }
}