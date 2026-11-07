package com.cqcnihao.bigx.utils.mapper.notification;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知数据传输对象
 */
@Data
public class NotificationDTO {

    private Long id;

    @NotNull(message = "用户ID不能为空")
    @Positive(message = "用户ID必须为正整数")
    private Long userId;

    @NotNull(message = "通知类型不能为空")
    @Pattern(regexp = "^(ORDER_STATUS|PAYMENT|PROMOTION|SYSTEM|REVIEW)$", 
             message = "通知类型只能是 ORDER_STATUS、PAYMENT、PROMOTION、SYSTEM 或 REVIEW")
    private String type;

    @NotNull(message = "通知标题不能为空")
    @Size(max = 100, message = "通知标题不能超过100字符")
    private String title;

    @NotNull(message = "通知内容不能为空")
    @Size(max = 2000, message = "通知内容不能超过2000字符")
    private String content;

    private Long businessId;

    private Boolean read;

    @Pattern(regexp = "^(UNREAD|READ|DELETED)$", message = "通知状态只能是 UNREAD、READ 或 DELETED")
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime readTime;
}
