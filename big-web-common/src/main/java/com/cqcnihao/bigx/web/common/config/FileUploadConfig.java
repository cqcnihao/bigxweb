package com.cqcnihao.bigx.web.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 文件上传配置
 * 统一配置文件上传路径、大小限制等
 * 
 * @author bigweb
 */
@Configuration
@ConditionalOnProperty(prefix = "file.upload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileUploadConfig implements WebMvcConfigurer {

    /**
     * 配置静态资源映射
     * 将上传目录映射为可访问的URL
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 文件上传目录映射
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:./uploads/")
                .setCachePeriod(3600);  // 缓存1小时
        
        // 静态资源目录映射
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(86400);  // 缓存24小时
    }
}
