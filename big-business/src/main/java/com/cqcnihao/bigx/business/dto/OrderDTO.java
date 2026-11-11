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
public class OrderDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "订单号不能为空")
    @Size(max = 64, message = "订单号最长 64 位")
    private String orderNo;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "客户名称最长 128 位")
    private String customerName;

    @DecimalMin(value = "0", message = "订单金额不能为负")
    private BigDecimal totalAmount;

    @Min(value = 0, message = "商品数量不能为负")
    private Integer itemCount;

    @Size(max = 1024, message = "备注最长 1024 位")
    private String remark;
}