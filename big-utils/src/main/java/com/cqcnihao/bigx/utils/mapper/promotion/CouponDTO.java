package com.cqcnihao.bigx.utils.mapper.promotion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券数据传输对象
 */
@Data
public class CouponDTO {

    private Long id;

    @NotNull(message = "优惠券名称不能为空")
    @Size(max = 100, message = "优惠券名称不能超过100字符")
    private String name;

    @NotNull(message = "优惠券类型不能为空")
    @Pattern(regexp = "^(FIXED_AMOUNT|PERCENTAGE)$", message = "优惠券类型只能是 FIXED_AMOUNT 或 PERCENTAGE")
    private String type;

    private BigDecimal amount;

    private BigDecimal discount;

    private BigDecimal minAmount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime validStartTime;

    private LocalDateTime validEndTime;

    @Positive(message = "总数量必须为正整数")
    private Integer totalCount;

    private Integer remainingCount;

    @Positive(message = "每人限领数量必须为正整数")
    private Integer perUserLimit;

    @Pattern(regexp = "^(ACTIVE|INACTIVE|EXPIRED)$", message = "状态只能是 ACTIVE、INACTIVE 或 EXPIRED")
    private String status;

    private LocalDateTime createTime;
}
