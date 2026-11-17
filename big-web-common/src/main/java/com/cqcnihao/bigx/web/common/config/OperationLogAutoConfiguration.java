package com.cqcnihao.bigx.web.common.config;

import com.cqcnihao.bigx.web.common.aspect.OperationLogAspect;
import com.cqcnihao.bigx.web.common.mapper.OperationLogMapper;
import com.cqcnihao.bigx.web.common.service.OperationLogService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

/**
 * 操作日志审计自动配置
 * <p>
 * 统一装配操作日志所需的三个组件，宿主应用无需任何额外配置：
 * <ul>
 *   <li>{@link OperationLogMapper}：通过 {@code @MapperScan} 限定扫描本模块 mapper 包，
 *       避免与宿主应用的 {@code @MapperScan} 冲突</li>
 *   <li>{@link OperationLogService}：异步写入数据库</li>
 *   <li>{@link OperationLogAspect}：拦截标注 {@code @OperationLog} 的方法</li>
 * </ul>
 * <p>
 * 关闭开关：{@code audit.log.enabled=false}（默认开启）。
 * 启用前请先执行本模块 {@code sql/operation_log.sql} 建表脚本。
 *
 * @author bigweb
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "audit.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.cqcnihao.bigx.web.common.mapper")
public class OperationLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperationLogService operationLogService(OperationLogMapper operationLogMapper,
                                                   ObjectProvider<TaskExecutor> taskExecutorProvider) {
        return new OperationLogService(operationLogMapper, taskExecutorProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(OperationLogService operationLogService,
                                                 ObjectProvider<tools.jackson.databind.ObjectMapper> objectMapperProvider) {
        return new OperationLogAspect(operationLogService, objectMapperProvider);
    }
}