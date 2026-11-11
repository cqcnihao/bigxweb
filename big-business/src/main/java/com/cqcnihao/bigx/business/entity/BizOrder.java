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
@TableName("biz_order")
public class BizOrder implements Serializable, Signable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int STATUS_DRAFT_CREATE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_PENDING_UPDATE = 2;
    public static final int STATUS_PENDING_DELETE = 3;

    public static final String BIZ_TYPE = BizType.ORDER.code();

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String customerName;

    private BigDecimal totalAmount;

    private Integer itemCount;

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
                    "C", ns(id), ns(orderNo), ns(customerName),
                    ns(totalAmount), ns(itemCount), ns(remark));
            case STATUS_PENDING_UPDATE -> String.join("|", "U", ns(id), ns(pendingData));
            case STATUS_PENDING_DELETE -> String.join("|", "D", ns(id), ns(orderNo), ns(customerName));
            default -> String.join("|", "A", ns(id), ns(orderNo));
        };
    }

    private static String ns(Object o) {
        return o == null ? "" : o.toString();
    }
}