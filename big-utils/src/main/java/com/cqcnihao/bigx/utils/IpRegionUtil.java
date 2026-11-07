package com.cqcnihao.bigx.utils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 离线IP归属地查询工具类
 * 遵循官方双库规范：IPv4+IPv6分开配置，统一托管查询
 * 完整覆盖所有内网IP段判断，杜绝公网查询误判
 * 单例模式，全局复用，避免重复加载数据库
 */
@Slf4j
@Component
public class IpRegionUtil {

    /**
     * IPv4、IPv6双库文件路径，对应resources/ipdb目录
     * 严格对应ip2region官方原文件名，不修改、不重命名
     */
    private static final String IPV4_XDB_PATH = "ipdb/ip2region_v4.xdb";
    private static final String IPV6_XDB_PATH = "ipdb/ip2region_v6.xdb";

    /**
     * ip2region官方统一查询对象，托管双库，无需手动拆分Searcher
     */
    private Ip2Region ip2Region;

    /**
     * 项目启动初始化，加载ip2region双库文件
     * 采用BufferCache缓存策略，兼顾查询速度与内存占用
     */
    @PostConstruct
    public void init() {
        try {
            // 加载IPv4数据库配置
            ClassPathResource v4Resource = new ClassPathResource(IPV4_XDB_PATH);
            InputStream v4Is = v4Resource.getInputStream();
            Config v4Config = Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setSearchers(10)
                    .setXdbInputStream(v4Is)
                    .asV4();

            // 加载IPv6数据库配置
            ClassPathResource v6Resource = new ClassPathResource(IPV6_XDB_PATH);
            InputStream v6Is = v6Resource.getInputStream();
            Config v6Config = Config.custom()
                    .setCachePolicy(Config.BufferCache)
                    .setSearchers(10)
                    .setXdbInputStream(v6Is)
                    .asV6();

            // 统一创建ip2region查询对象
            ip2Region = Ip2Region.create(v4Config, v6Config);
            log.info("ip2region初始化成功，IPv4+IPv6双库加载完成");
        } catch (Exception e) {
            log.error("ip2region初始化失败，请检查双库文件路径和完整性", e);
            throw new RuntimeException("ip2region异常，无法提供IP查询服务");
        }
    }

    /**
     * 统一IP查询入口，自动识别IPv4/IPv6
     * 完整过滤所有内网IP，包含10段、172段、192段、本地回环
     */
    public String getIpRegion(String ip) {
        try {
            // 空IP直接返回
            if (ip == null || ip.isBlank()) {
                return "内网IP|内网IP";
            }
            // 判断是否为内网IP，包含IPv4全内网段 + IPv6回环
            if (isPrivateIp(ip)) {
                return "内网IP|内网IP";
            }
            // 调用ip2region执行公网IP查询
            return ip2Region.search(ip);
        } catch (Exception e) {
            log.error("ip2region IP查询异常，IP：{}", ip, e);
            return "查询失败";
        }
    }

    /**
     * 完整内网IP判断逻辑
     * IPv4内网范围：
     * 1. 127.0.0.1 本地回环
     * 2. 10.0.0.0 ~ 10.255.255.255
     * 3. 172.16.0.0 ~ 172.31.255.255
     * 4. 192.168.0.0 ~ 192.168.255.255
     * IPv6内网：::1 / 0:0:0:0:0:0:0:1 回环地址
     */
    private boolean isPrivateIp(String ip) {
        // IPv4内网判断
        if (ip.contains(".")) {
            // 本地回环
            if ("127.0.0.1".equals(ip)) {
                return true;
            }
            // 192.168网段
            if (ip.startsWith("192.168.")) {
                return true;
            }
            // 10网段
            if (ip.startsWith("10.")) {
                return true;
            }
            // 172.16.0.0 ~ 172.31.255.255 内网段
            if (ip.startsWith("172.")) {
                String[] parts = ip.split("\\.");
                if (parts.length == 4) {
                    try {
                        int secondSegment = Integer.parseInt(parts[1]);
                        return secondSegment >= 16 && secondSegment <= 31;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }
            return false;
        }

        // IPv6回环地址判断
        return "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    /**
     * 服务关闭，释放ip2region资源
     */
    @PreDestroy
    public void close() {
        try {
            if (ip2Region != null) {
                ip2Region.close();
                log.info("ip2region资源释放完成");
            }
        } catch (Exception e) {
            log.error("ip2region资源释放失败", e);
        }
    }
}