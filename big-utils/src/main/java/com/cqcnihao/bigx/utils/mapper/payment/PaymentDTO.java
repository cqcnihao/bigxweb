package com.cqcnihao.bigx.utils.mapper.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付数据传输对象
 */
@Data
public class PaymentDTO {

    private Long id;

    @NotNull(message = "订单ID不能为空")
    @Positive(message = "订单ID必须为正整数")
    private Long orderId;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long userId;

    @NotNull(message = "支付金额不能为空")
    @Positive(message = "支付金额必须为正数")
    private BigDecimal amount;

    @Pattern(regexp = "^(ALIPAY|WECHAT|BANK)$", message = "支付方式只能是 ALIPAY、WECHAT 或 BANK")
    private String paymentMethod;

    @Pattern(regexp = "^(PENDING|SUCCESS|FAILED|REFUNDED)$", message = "支付状态只能是 PENDING、SUCCESS、FAILED 或 REFUNDED")
    private String status;

    private String transactionNo;

    private BigDecimal refundAmount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
