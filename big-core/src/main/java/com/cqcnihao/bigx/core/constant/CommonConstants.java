package com.cqcnihao.bigx.core.constant;

/**
 * 通用常量定义
 */
public final class CommonConstants {

    private CommonConstants() {
        // 禁止实例化
    }

    // ==================== HTTP 状态码 ====================
    public static final String CODE_SUCCESS = "200";
    public static final String CODE_BAD_REQUEST = "400";
    public static final String CODE_UNAUTHORIZED = "401";
    public static final String CODE_FORBIDDEN = "403";
    public static final String CODE_NOT_FOUND = "404";
    public static final String CODE_INTERNAL_ERROR = "500";
    public static final String CODE_SERVICE_UNAVAILABLE = "503";

    // ==================== 通用消息 ====================
    public static final String MSG_SUCCESS = "操作成功";
    public static final String MSG_FAIL = "操作失败";
    public static final String MSG_UNAUTHORIZED = "未登录，请先登录";
    public static final String MSG_FORBIDDEN = "无权限访问";
    public static final String MSG_NOT_FOUND = "请求的资源不存在";
    public static final String MSG_INTERNAL_ERROR = "系统繁忙，请稍后再试";

    // ==================== 请求头 ====================
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_REQUEST_FROM = "X-Request-From";
}
