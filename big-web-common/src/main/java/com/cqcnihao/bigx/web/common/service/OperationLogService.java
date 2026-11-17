package com.cqcnihao.bigx.web.common.service;

import com.cqcnihao.bigx.web.common.entity.OperationLogEntity;
import com.cqcnihao.bigx.web.common.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 操作日志服务
 * <p>
 * 异步保存操作日志，避免阻塞主业务流程。
 * 未显式注入线程池时退化为独立线程执行（不依赖 {@code @EnableAsync}，
 * 避免通用组件对宿主应用的异步配置产生副作用）。
 */
@Slf4j
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final ObjectProvider<TaskExecutor> taskExecutorProvider;
    private volatile TaskExecutor taskExecutor;

    public OperationLogService(OperationLogMapper operationLogMapper,
                               ObjectProvider<TaskExecutor> taskExecutorProvider) {
        this.operationLogMapper = operationLogMapper;
        this.taskExecutorProvider = taskExecutorProvider;
    }

    /**
     * 异步保存操作日志
     *
     * @param entity 操作日志实体
     */
    public void save(OperationLogEntity entity) {
        taskExecutor().execute(() -> persist(entity));
    }

    /**
     * 延迟获取线程池：优先使用宿主应用唯一的 {@link TaskExecutor}，
     * 取不到时退回独立线程执行，保证通用组件不依赖宿主的异步配置。
     */
    private TaskExecutor taskExecutor() {
        if (taskExecutor == null) {
            synchronized (this) {
                if (taskExecutor == null) {
                    TaskExecutor provided = taskExecutorProvider.getIfUnique();
                    taskExecutor = provided != null
                            ? provided : new SimpleAsyncTaskExecutor("operation-log-");
                }
            }
        }
        return taskExecutor;
    }

    private void persist(OperationLogEntity entity) {
        try {
            operationLogMapper.insert(entity);
            log.debug("操作日志已保存 | module: {} | type: {} | uri: {} | cost: {}ms",
                    entity.getModule(), entity.getOperationType(),
                    entity.getRequestUri(), entity.getExecutionTime());
        } catch (Exception e) {
            // 日志保存失败不应影响主业务
            log.error("操作日志保存失败 | uri: {} | error: {}",
                    entity.getRequestUri(), e.getMessage(), e);
        }
    }
}