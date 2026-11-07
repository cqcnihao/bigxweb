package com.cqcnihao.bigx.web.common.advice;

import com.cqcnihao.bigx.common.IResult;
import com.cqcnihao.bigx.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局响应体增强器：自动将 Controller 返回值包装为 Result.success(data)
 * <p>
 * 排除以下情况：
 * - 返回类型已是 {@link IResult} 实现（Result、ApiResponse 等，避免重复包装）
 * - 方法标注 @IgnoreResultWrapper（可选注解）
 * <p>
 * 注意：本 Advice 必须<b>先于</b> big-Encrypt 的 SmEncryptResponseBodyAdvice 执行
 * （先包装成 Result，再由加密 Advice 加密 data 部分），否则会先加密再被包装一层，前端解不出来。
 * <p>
 * 顺序值需恒小于 {@code com.cqcnihao.bigx.encrypt.EncryptOrders.ENCRYPT_RESPONSE}
 * （当前为 {@code Ordered.LOWEST_PRECEDENCE - 100}）；若调整此处，务必同步核对 big-Encrypt。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ResultResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !IResult.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return Result.success();
        }
        return Result.success(body);
    }
}