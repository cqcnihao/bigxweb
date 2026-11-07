package com.cqcnihao.bigx.utils.mapper.promotion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券数据传输对象
 */
@Data
public class UserCouponDTO {

    private Long id;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long userId;

    @NotNull(message = "优惠券ID不能为空")
    @Positive(message = "优惠券ID必须为正整数")
    private Long couponId;

    private String couponName;

    @Pattern(regexp = "^(FIXED_AMOUNT|PERCENTAGE)$", message = "优惠券类型只能是 FIXED_AMOUNT 或 PERCENTAGE")
    private String couponType;

    private java.math.BigDecimal amount;

    private java.math.BigDecimal discount;

    private java.math.BigDecimal minAmount;

    private LocalDateTime validStartTime;

    private LocalDateTime validEndTime;

    @Pattern(regexp = "^(UNUSED|USED|EXPIRED)$", message = "使用状态只能是 UNUSED、USED 或 EXPIRED")
    private String status;

    private Long orderId;

    private LocalDateTime usedTime;

    private LocalDateTime createTime;
}
