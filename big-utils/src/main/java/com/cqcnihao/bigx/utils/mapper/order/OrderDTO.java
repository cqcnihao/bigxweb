package com.cqcnihao.bigx.utils.mapper.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单数据传输对象
 */
@Data
public class OrderDTO {

    private Long id;

    /** 用户ID */
    private Long userId;

    /** 订单编号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal amount;

    /** 订单状态: PENDING / PAID / SHIPPED / COMPLETED / CANCELLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
