package com.cqcnihao.bigx.utils.interceptor;

import com.cqcnihao.bigx.utils.TraceIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * TraceId 链路追踪拦截器
 * 为每个请求生成唯一的 TraceId，用于日志追踪和问题排查
 * 
 * 使用方式：
 * 1. 在各模块的 WebConfig 中注册此拦截器
 * 2. 日志配置中添加 %X{TraceId} 输出
 * 
 * @author bigweb
 */
@Slf4j
public class TraceIdInterceptor implements HandlerInterceptor {
    
    private static final String TRACE_ID_KEY = "TraceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 优先使用请求头中的 TraceId（支持跨服务传递）
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceIdGenerator.generateTraceId();
        }
        
        // 放入 MDC 供日志使用
        MDC.put(TRACE_ID_KEY, traceId);
        
        // 放入响应头返回给客户端
        response.setHeader(TRACE_ID_HEADER, traceId);
        
        log.debug("请求开始: {} {}, TraceId: {}", 
                request.getMethod(), request.getRequestURI(), traceId);
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        // 清理 MDC，防止内存泄漏
        MDC.remove(TRACE_ID_KEY);
        
        log.debug("请求完成: {} {}, Status: {}", 
                request.getMethod(), request.getRequestURI(), response.getStatus());
    }
}
