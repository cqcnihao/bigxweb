package com.cqcnihao.bigx.utils.mapper.promotion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动数据传输对象
 */
@Data
public class ActivityDTO {

    private Long id;

    @NotNull(message = "活动名称不能为空")
    @Size(max = 200, message = "活动名称不能超过200字符")
    private String name;

    @NotNull(message = "活动类型不能为空")
    @Pattern(regexp = "^(FLASH_SALE|DISCOUNT|GIFT)$", message = "活动类型只能是 FLASH_SALE、DISCOUNT 或 GIFT")
    private String type;

    @Size(max = 500, message = "活动描述不能超过500字符")
    private String description;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Pattern(regexp = "^(ACTIVE|INACTIVE|ENDED)$", message = "活动状态只能是 ACTIVE、INACTIVE 或 ENDED")
    private String status;

    private List<Long> productIds;

    private BigDecimal discount;

    private BigDecimal amount;

    private LocalDateTime createTime;
}
