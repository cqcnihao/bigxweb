package com.cqcnihao.bigx.web.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqcnihao.bigx.web.common.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
