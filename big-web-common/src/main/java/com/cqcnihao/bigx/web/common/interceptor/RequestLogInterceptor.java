package com.cqcnihao.bigx.web.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 请求日志拦截器
 * 记录每个请求的详细信息，便于调试和监控
 * 
 * @author bigweb
 */
@Slf4j
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_KEY = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求开始时间
        request.setAttribute(START_TIME_KEY, System.currentTimeMillis());
        
        // 记录请求信息
        log.info(">>> 请求开始: {} {} | IP: {} | User-Agent: {}",
                request.getMethod(),
                request.getRequestURI(),
                getClientIp(request),
                request.getHeader("User-Agent"));
        
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) {
        // 可以在这里修改响应
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        // 计算请求耗时
        Long startTime = (Long) request.getAttribute(START_TIME_KEY);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
        
        // 记录响应信息
        log.info("<<< 请求完成: {} {} | 状态: {} | 耗时: {}ms",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
        
        // 如果有异常，记录异常
        if (ex != null) {
            log.error("请求异常: {} {}", request.getMethod(), request.getRequestURI(), ex);
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
