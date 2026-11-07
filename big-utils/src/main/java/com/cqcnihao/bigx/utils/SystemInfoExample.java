package com.cqcnihao.bigx.utils;


import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
  * 系统硬件信息采集工具类示例
  * 封装底层调用，提供静态方法供上层使用
  * 
  * @deprecated 此类仅用于演示，生产环境应使用 SystemInfoUtil 工具类
  */
@Slf4j
@Deprecated
public class SystemInfoExample {

    public static void main(String[] args) {
          try {
            log.info("--------------------");
            // 1. 基本使用
            log.info("=== 系统摘要 ===");
            Map<String, Object> summary = SystemInfoUtil.getSystemSummary();
            log.info("操作系统: {}", ((Map)summary.get("os")).get("version"));
            log.info("CPU: {}", ((Map)summary.get("cpu")).get("name"));
            log.info("CPU使用率: {}", ((Map)summary.get("cpu")).get("usage"));
            log.info("系统运行时间: {}", ((Map)summary.get("system")).get("uptime"));

            // 2. 获取详细CPU信息
            log.info("\n=== CPU详细使用率 ===");
            Map<String, Double> cpuUsage = SystemInfoUtil.getCpuDetailedUsage();
            cpuUsage.forEach((k, v) -> log.info("{}: {}%", k, String.format("%.1f", v)));

            // 3. 获取磁盘信息
            log.info("\n=== 磁盘使用情况 ===");
            List<Map<String, Object>> disks = SystemInfoUtil.getDiskUsage();
            disks.forEach(disk -> {
                log.info("{} - 已用: {} ({} / {})", 
                        disk.get("name"), disk.get("usage"), 
                        disk.get("usedFormatted"), disk.get("totalFormatted"));
            });

            // 4. 启动性能监控
            SystemInfoUtil.startMonitor();

            // 5. 等待一段时间收集数据
            Thread.sleep(10000);

            // 6. 获取监控数据
            log.info("\n=== 性能监控数据 ===");
            List<Double> cpuHistory = SystemInfoUtil.getCpuUsageHistory();
            log.info("CPU使用率历史: {}", cpuHistory);

            // 7. 健康检查
            log.info("\n=== 系统健康检查 ===");
            Map<String, Object> health = SystemInfoUtil.healthCheck();
            log.info("健康状态: {}", health.get("status"));

            // 8. 获取警告
            List<String> warnings = SystemInfoUtil.getWarnings();
            if (!warnings.isEmpty()) {
                log.warn("警告信息:");
                warnings.forEach(log::warn);
            }

            // 9. 停止监控
            SystemInfoUtil.stopMonitor();

            // 10. 优雅关闭
            SystemInfoUtil.shutdown();

        } catch (Exception e) {
            log.error("系统信息采集失败", e);
        }
    }
}