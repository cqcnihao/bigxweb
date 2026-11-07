package com.cqcnihao.bigx.utils.mapper.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价数据传输对象
 */
@Data
public class ReviewDTO {

    private Long id;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long userId;

    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须为正整数")
    private Long productId;

    @NotNull(message = "订单ID不能为空")
    @Positive(message = "订单ID必须为正整数")
    private Long orderId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer rating;

    @Size(max = 2000, message = "评价内容不能超过2000字符")
    private String content;

    private String images;

    private Boolean anonymous;

    private Integer helpfulCount;

    @Pattern(regexp = "^(PENDING|APPROVED|REJECTED)$", message = "评价状态只能是 PENDING、APPROVED 或 REJECTED")
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
