package com.cqcnihao.bigx.utils.mapper.price;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格数据传输对象
 */
@Data
public class PriceDTO {

    private Long id;

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 现价（折扣后） */
    private BigDecimal currentPrice;

    /** 货币单位: CNY / USD */
    private String currency;

    /** 最后更新时间 */
    private LocalDateTime lastUpdateTime;
}
