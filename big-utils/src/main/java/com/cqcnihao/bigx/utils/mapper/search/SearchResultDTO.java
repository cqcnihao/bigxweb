package com.cqcnihao.bigx.utils.mapper.search;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 搜索结果数据传输对象
 */
@Data
public class SearchResultDTO {

    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须为正整数")
    private Long productId;

    @NotNull(message = "商品名称不能为空")
    private String productName;

    private String category;

    private BigDecimal price;

    private String description;

    private String imageUrl;

    private Double score;

    private Integer sales;
}
