package com.cqcnihao.bigx.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cqcnihao.bigx.common.annotation.Desensitize;
import com.cqcnihao.bigx.common.enums.DesensitizeType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("biz_product")
public class BizProduct implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productName;

    private String productCode;

    private BigDecimal price;

    private Integer stock;

    private String category;

    private String description;

    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;
}