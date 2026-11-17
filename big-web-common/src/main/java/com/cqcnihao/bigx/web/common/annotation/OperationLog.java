package com.cqcnihao.bigx.web.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志审计注解
 * <p>
 * 标注在 Controller 方法上，AOP 切面自动记录请求信息、响应信息和执行元数据到数据库。
 *
 * <p><strong>使用示例：</strong>
 * <pre>
 * &#64;OperationLog(module = "用户管理", operationType = "QUERY", description = "查询用户信息")
 * public Result&lt;UserDTO&gt; queryUser(@PathVariable Long userId) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 所属模块（如：用户管理、订单管理）
     */
    String module() default "";

    /**
     * 操作类型（如：QUERY / CREATE / UPDATE / DELETE）
     */
    String operationType() default "QUERY";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数（默认 true）
     */
    boolean saveRequestData() default true;

    /**
     * 是否记录响应数据（默认 true）
     */
    boolean saveResponseData() default true;
}
