package com.cqcnihao.bigx.web.common.aspect;

import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.web.common.annotation.OperationLog;
import com.cqcnihao.bigx.web.common.entity.OperationLogEntity;
import com.cqcnihao.bigx.web.common.service.OperationLogService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志审计切面
 * <p>
 * 拦截所有标注 {@link OperationLog} 注解的 Controller 方法，
 * 自动记录请求信息、响应信息、执行耗时和链路追踪元数据到数据库。
 */
@Slf4j
@Aspect
public class OperationLogAspect {

    /** 响应数据最大存储长度（字符），超过则截断 */
    private static final int MAX_RESPONSE_LENGTH = 2000;

    /** 请求参数最大存储长度（字符） */
    private static final int MAX_REQUEST_LENGTH = 2000;

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Value("${spring.application.name:WebOpp}")
    private String serviceName;

    @Value("${audit.log.enabled:true}")
    private boolean auditEnabled;

    public OperationLogAspect(OperationLogService operationLogService,
                              ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.operationLogService = operationLogService;
        ObjectMapper existing = objectMapperProvider.getIfAvailable();
        this.objectMapper = existing != null ? existing : new ObjectMapper();
    }

    @Around("@annotation(com.cqcnihao.bigx.web.common.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        OperationLog operationLog = resolveAnnotation(joinPoint);
        // 审计未启用时直接放行
        if (!auditEnabled || operationLog == null) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        OperationLogEntity entity = new OperationLogEntity();

        // ====== 1. 提取请求信息 ======
        HttpServletRequest request = getRequest();
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setRequestUri(request.getRequestURI());
            entity.setClientIp(getClientIp(request));
            entity.setClientId(request.getHeader("X-Client-Id"));
            entity.setUserId(request.getHeader("X-User-Id"));
            entity.setRequestHeaders(extractHeaders(request));
        }

        // 注解元数据
        entity.setModule(operationLog.module());
        entity.setOperationType(operationLog.operationType());
        entity.setDescription(operationLog.description());

        // 请求参数
        if (operationLog.saveRequestData()) {
            entity.setRequestParams(extractParams(joinPoint));
        }

        // ====== 2. 执行目标方法 ======
        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            exception = ex;
            throw ex;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            entity.setExecutionTime(cost);

            // ====== 3. 提取响应信息 ======
            fillResponse(entity, result, exception, operationLog);

            // ====== 4. 提取链路追踪元数据 ======
            fillContext(entity, request);

            entity.setServiceName(serviceName);
            entity.setCreateTime(LocalDateTime.now());

            // ====== 5. 异步保存 ======
            try {
                operationLogService.save(entity);
            } catch (Exception e) {
                log.warn("操作日志异步提交失败: {}", e.getMessage());
            }
        }
    }

    // ==================== 响应信息提取 ====================

    private void fillResponse(OperationLogEntity entity, Object result,
                              Throwable exception, OperationLog operationLog) {
        if (exception != null) {
            entity.setResponseStatus("ERROR");
            entity.setResponseCode("500");
            entity.setErrorMessage(truncate(exception.getMessage(), 500));
            return;
        }

        if (result instanceof Result<?> r) {
            entity.setResponseStatus(r.isSuccess() ? "SUCCESS" : "FAIL");
            entity.setResponseCode(r.getCode());
            if (operationLog.saveResponseData()) {
                entity.setResponseData(truncate(toJson(r), MAX_RESPONSE_LENGTH));
            }
        } else if (result != null && operationLog.saveResponseData()) {
            entity.setResponseStatus("SUCCESS");
            entity.setResponseData(truncate(toJson(result), MAX_RESPONSE_LENGTH));
        } else {
            entity.setResponseStatus("SUCCESS");
        }
    }

    // ==================== 上下文信息提取 ====================

    /**
     * 链路追踪元数据：优先取网关注入的请求头，TraceId 兜底取 MDC
     * （TraceIdInterceptor 会把请求头或生成的 TraceId 写入 MDC）。
     */
    private void fillContext(OperationLogEntity entity, HttpServletRequest request) {
        String traceId = null;
        if (request != null) {
            traceId = request.getHeader("X-Trace-Id");
            entity.setRequestFrom(request.getHeader("X-Request-From"));
            entity.setApiVersion(request.getHeader("X-Api-Version"));
            if (entity.getUserId() == null) {
                entity.setUserId(request.getHeader("X-User-Id"));
            }
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = MDC.get("TraceId");
        }
        entity.setTraceId(traceId);
    }

    // ==================== 请求头提取 ====================

    private String extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        String[] headerNames = {
                "X-Trace-Id", "X-Client-Id", "X-Api-Version",
                "X-Gateway-Name", "X-Request-From", "X-User-Id",
                "User-Agent", "Accept", "Content-Type"
        };
        for (String name : headerNames) {
            String value = request.getHeader(name);
            if (value != null && !value.isEmpty()) {
                headers.put(name, value);
            }
        }
        return truncate(toJson(headers), 500);
    }

    // ==================== 参数提取 ====================

    private String extractParams(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames == null || paramNames.length == 0) {
                return null;
            }
            Map<String, Object> params = new LinkedHashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                // 跳过 Servlet / 文件上传等不可序列化参数，其余（含 DTO）照常记录
                if (args[i] != null && !isUnserializable(args[i])) {
                    params.put(paramNames[i], maskIfSensitive(paramNames[i], args[i]));
                }
            }
            return truncate(toJson(params), MAX_REQUEST_LENGTH);
        } catch (Exception e) {
            log.debug("参数提取失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从被拦截的方法上读取 {@link OperationLog} 注解（兼容接口方法声明注解的情况）。
     */
    private OperationLog resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        if (annotation != null) {
            return annotation;
        }
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass() : sig.getDeclaringType();
        try {
            Method targetMethod = targetClass.getMethod(method.getName(), method.getParameterTypes());
            return targetMethod.getAnnotation(OperationLog.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) + "...[TRUNCATED]" : str;
    }

    private boolean isUnserializable(Object arg) {
        return arg instanceof jakarta.servlet.ServletRequest
                || arg instanceof jakarta.servlet.ServletResponse
                || arg instanceof java.io.InputStream
                || arg instanceof java.io.OutputStream
                || arg instanceof org.springframework.web.multipart.MultipartFile;
    }

    /**
     * 敏感参数脱敏：参数名含密码/密钥/令牌等关键字时，直接以掩码代替原值。
     */
    private Object maskIfSensitive(String name, Object value) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("password") || lower.contains("passwd")
                || lower.contains("secret") || lower.contains("token")) {
            return "******";
        }
        return value;
    }
}