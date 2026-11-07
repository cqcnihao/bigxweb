package com.cqcnihao.bigx.utils.mapper.inventory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存数据传输对象
 */
@Data
public class InventoryDTO {

    private Long id;

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 库存数量 */
    private Integer quantity;

    /** 仓库位置 */
    private String warehouse;

    /** 最后更新时间 */
    private LocalDateTime lastUpdateTime;
}
