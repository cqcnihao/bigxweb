package com.cqcnihao.bigx.utils.mapper.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车数据传输对象
 */
@Data
public class CartDTO {

    private Long id;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long userId;

    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须为正整数")
    private Long productId;

    @NotNull(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称不能超过200字符")
    private String productName;

    @NotNull(message = "商品价格不能为空")
    private BigDecimal price;

    @NotNull(message = "购买数量不能为空")
    @Positive(message = "购买数量必须为正整数")
    private Integer quantity;

    private String productImage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
