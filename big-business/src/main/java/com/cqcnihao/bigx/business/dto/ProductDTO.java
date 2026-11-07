package com.cqcnihao.bigx.business.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProductDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称最长 128 位")
    private String productName;

    @NotBlank(message = "商品编码不能为空")
    @Size(max = 64, message = "商品编码最长 64 位")
    private String productCode;

    @DecimalMin(value = "0", message = "价格不能为负")
    private BigDecimal price;

    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    @Size(max = 64, message = "分类最长 64 位")
    private String category;

    @Size(max = 1024, message = "描述最长 1024 位")
    private String description;
}