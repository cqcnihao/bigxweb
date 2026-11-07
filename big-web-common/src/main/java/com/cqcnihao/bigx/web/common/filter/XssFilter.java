package com.cqcnihao.bigx.web.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * XSS过滤器
 * 用于防止跨站脚本攻击
 */
@Slf4j
@Component
@Order(1)
public class XssFilter implements Filter {
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("XSS过滤器初始化成功");
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // 包装Request，对参数进行XSS过滤
        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(httpRequest);
        
        chain.doFilter(xssRequest, response);
    }
    
    @Override
    public void destroy() {
        log.info("XSS过滤器销毁");
    }
}
