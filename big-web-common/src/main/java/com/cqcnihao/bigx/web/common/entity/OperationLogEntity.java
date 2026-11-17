package com.cqcnihao.bigx.web.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志审计实体
 * <p>
 * 记录每次 API 请求的完整审计信息，包括请求参数、响应结果、执行耗时、链路追踪等。
 */
@Data
@TableName("operation_log")
public class OperationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    // ==================== 请求信息 ====================

    /** 请求方法: GET / POST / PUT / DELETE */
    private String requestMethod;

    /** 请求 URI */
    private String requestUri;

    /** 客户端 IP */
    private String clientIp;

    /** 客户端标识 (X-Client-Id) */
    private String clientId;

    /** 用户标识 (X-User-Id) */
    private String userId;

    /** 请求参数（JSON） */
    private String requestParams;

    /** 请求头摘要（JSON） */
    private String requestHeaders;

    // ==================== 响应信息 ====================

    /** 响应状态码: SUCCESS / FAIL / ERROR */
    private String responseStatus;

    /** 响应码 */
    private String responseCode;

    /** 响应数据（JSON，截断存储） */
    private String responseData;

    /** 异常信息（如有） */
    private String errorMessage;

    // ==================== 元数据 ====================

    /** 所属模块 */
    private String module;

    /** 操作类型: QUERY / CREATE / UPDATE / DELETE */
    private String operationType;

    /** 操作描述 */
    private String description;

    /** 执行耗时 (ms) */
    private Long executionTime;

    /** 链路追踪 ID (X-Trace-Id) */
    private String traceId;

    /** 请求来源 (X-Request-From) */
    private String requestFrom;

    /** API 版本 (X-Api-Version) */
    private String apiVersion;

    /** 服务名称 */
    private String serviceName;

    /** 记录创建时间 */
    private LocalDateTime createTime;
}
