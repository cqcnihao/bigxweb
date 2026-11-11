package com.cqcnihao.bigx.business.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class InvoiceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "发票号不能为空")
    @Size(max = 64, message = "发票号最长 64 位")
    private String invoiceNo;

    @Size(max = 64, message = "关联订单号最长 64 位")
    private String orderNo;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "客户名称最长 128 位")
    private String customerName;

    @DecimalMin(value = "0", message = "开票金额不能为负")
    private BigDecimal invoiceAmount;

    @DecimalMin(value = "0", message = "税率不能为负")
    private BigDecimal taxRate;

    @DecimalMin(value = "0", message = "税额不能为负")
    private BigDecimal taxAmount;

    @DecimalMin(value = "0", message = "价税合计不能为负")
    private BigDecimal totalAmount;

    @Size(max = 32, message = "发票类型最长 32 位")
    private String invoiceType;

    @Size(max = 1024, message = "备注最长 1024 位")
    private String remark;
}