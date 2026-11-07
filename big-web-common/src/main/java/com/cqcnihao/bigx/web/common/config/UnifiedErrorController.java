package com.cqcnihao.bigx.web.common.config;

import com.cqcnihao.bigx.common.Result;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一错误页面处理
 * 将所有错误返回为统一的 JSON 格式
 * <p>
 * 本类由 {@link UnifiedErrorControllerConfig} 以 @Bean 方式注册，
 * 不能直接列入 AutoConfiguration.imports（见该类的说明）。
 *
 * @author bigweb
 */
@Slf4j
@RestController
public class UnifiedErrorController implements ErrorController {

    /**
     * 处理所有错误
     */
    @RequestMapping("/error")
    public Result<Object> handleError(HttpServletRequest request) {
        // 获取错误状态码
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object errorMessage = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        
        int status = statusCode != null ? Integer.parseInt(statusCode.toString()) : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String message = errorMessage != null ? errorMessage.toString() : "服务器内部错误";
        String path = requestUri != null ? requestUri.toString() : request.getRequestURI();
        
        // 记录错误日志
        log.error("请求错误: {} {} - 状态码: {}, 错误信息: {}", 
                request.getMethod(), path, status, message);
        
        // 根据状态码返回不同的错误信息
        return switch (status) {
            case 400 -> Result.fail("400", "请求参数错误: " + message);
            case 401 -> Result.fail("401", "未授权，请先登录");
            case 403 -> Result.fail("403", "拒绝访问，权限不足");
            case 404 -> Result.fail("404", "请求的资源不存在: " + path);
            case 405 -> Result.fail("405", "请求方法不允许");
            case 500 -> Result.fail("500", "服务器内部错误，请稍后重试");
            case 502 -> Result.fail("502", "网关错误");
            case 503 -> Result.fail("503", "服务暂时不可用，请稍后重试");
            default -> Result.fail(String.valueOf(status), "请求失败: " + message);
        };
    }
}