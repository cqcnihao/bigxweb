package com.cqcnihao.bigx.web.common.controller;

import com.cqcnihao.bigx.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 基础 Controller
 * 提供通用的健康检查、服务信息等接口
 * 所有业务模块可以继承此类获得基础功能
 * 
 * @author bigweb
 */
@Tag(name = "基础服务", description = "健康检查和服务信息")
@RestController
@RequestMapping("/api/base")
public class BaseController {

    /**
     * 健康检查接口
     */
    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("service", "bigxweb");
        return Result.success(data);
    }

    /**
     * 获取服务信息
     */
    @Operation(summary = "服务信息", description = "获取当前服务的基本信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Bigxweb Service");
        data.put("version", "1.4.8-SNAPSHOT");
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("timestamp", LocalDateTime.now().toString());
        return Result.success(data);
    }
}
