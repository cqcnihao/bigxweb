package com.cqcnihao.bigx.common;

import com.cqcnihao.bigx.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.stream.Collectors;

// Sa-Token 异常类（可选，仅在 Sa-Token 依赖存在时加载）
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;

/**
 * 全局异常处理器
 * 统一处理所有模块的异常，返回标准化的 Result 响应
 * 仅在 Servlet 环境（Spring MVC）中生效
 * 
 * @author bigweb
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE) // 子类可通过 @Order(更小值) 获得更高优先级
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    /**
     * 业务异常
     * 用于处理自定义的业务逻辑错误
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@RequestBody JSON）
     * 处理 @Valid/@Validated 注解的校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 参数校验异常（表单绑定）
     * 处理表单提交时的参数校验失败
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 单个参数校验异常
     * 处理 @RequestParam/@PathVariable 等单个参数校验
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数约束校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = "缺少必要参数: " + e.getParameterName();
        log.warn(message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        String message = "不支持 " + e.getMethod() + " 请求，支持的请求方法: " + e.getSupportedMethods();
        log.warn(message);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * SQL异常
     * 数据库操作异常的统一处理
     */
    @ExceptionHandler(SQLException.class)
    public Result<Void> handleSQLException(SQLException e) {
        log.error("数据库异常: SQLState={}, ErrorCode={}, Message={}", 
                e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "数据库操作失败，请联系管理员");
    }

    /**
     * 运行时异常
     * 处理未预期的运行时错误
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR.getCode(), e.getMessage());
    }

    /**
     * 其他异常
     * 兜底处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: {} - {}", e.getClass().getName(), e.getMessage(), e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "系统繁忙，请稍后再试");
    }

    // ==================== Sa-Token 认证异常处理 ====================
    // 以下方法仅在 Sa-Token 依赖存在时生效

    /**
     * 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    @ConditionalOnClass(name = "cn.dev33.satoken.exception.NotLoginException")
    public Result<Void> handleNotLoginException(NotLoginException e) {
        log.warn("未登录: {}", e.getMessage());
        return Result.error("401", "未登录，请先登录");
    }

    /**
     * 无权限异常
     */
    @ExceptionHandler(NotPermissionException.class)
    @ConditionalOnClass(name = "cn.dev33.satoken.exception.NotPermissionException")
    public Result<Void> handleNotPermissionException(NotPermissionException e) {
        log.warn("无权限: {}", e.getMessage());
        return Result.error("403", "无权限访问");
    }

    /**
     * 无角色异常
     */
    @ExceptionHandler(NotRoleException.class)
    @ConditionalOnClass(name = "cn.dev33.satoken.exception.NotRoleException")
    public Result<Void> handleNotRoleException(NotRoleException e) {
        log.warn("无角色: {}", e.getMessage());
        return Result.error("403", "无角色权限");
    }
}
