package com.cqcnihao.bigx.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cqcnihao.bigx.license.Signable;
import com.cqcnihao.bigx.license.annotation.BizType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_invoice")
public class BizInvoice implements Serializable, Signable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int STATUS_DRAFT_CREATE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_PENDING_UPDATE = 2;
    public static final int STATUS_PENDING_DELETE = 3;

    public static final String BIZ_TYPE = BizType.INVOICE.code();

    @TableId(type = IdType.AUTO)
    private Long id;

    private String invoiceNo;

    private String orderNo;

    private String customerName;

    private BigDecimal invoiceAmount;

    private BigDecimal taxRate;

    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    private String invoiceType;

    private String remark;

    private Integer status;

    private String pendingData;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    @Override
    public Object getBizId() {
        return id;
    }

    @Override
    public String getBizType() {
        return BIZ_TYPE;
    }

    @Override
    public String buildSignPayload() {
        int s = status == null ? STATUS_ACTIVE : status;
        return switch (s) {
            case STATUS_DRAFT_CREATE -> String.join("|",
                    "C", ns(id), ns(invoiceNo), ns(orderNo), ns(customerName),
                    ns(invoiceAmount), ns(taxRate), ns(taxAmount),
                    ns(totalAmount), ns(invoiceType), ns(remark));
            case STATUS_PENDING_UPDATE -> String.join("|", "U", ns(id), ns(pendingData));
            case STATUS_PENDING_DELETE -> String.join("|", "D", ns(id), ns(invoiceNo), ns(customerName));
            default -> String.join("|", "A", ns(id), ns(invoiceNo));
        };
    }

    private static String ns(Object o) {
        return o == null ? "" : o.toString();
    }
}