package com.cqcnihao.bigx.utils.mapper.product;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品数据传输对象
 */
@Data
public class ProductDTO {

    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品分类 */
    private String category;

    /** 商品价格 */
    private BigDecimal price;

    /** 商品描述 */
    private String description;

    /** 商品状态: ON_SALE / OFF_SHELF */
    private String status;
}
