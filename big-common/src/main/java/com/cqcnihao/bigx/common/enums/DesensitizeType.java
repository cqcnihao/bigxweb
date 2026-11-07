package com.cqcnihao.bigx.common.enums;

/**
 * 数据脱敏类型枚举
 */
public enum DesensitizeType {
    
    /**
     * 手机号脱敏
     * 示例: 138****1234
     */
    MOBILE,
    
    /**
     * 邮箱脱敏
     * 示例: test****@example.com
     */
    EMAIL,
    
    /**
     * 身份证号脱敏
     * 示例: 110***********1234
     */
    ID_CARD,
    
    /**
     * 银行卡号脱敏
     * 示例: 6222 **** **** 1234
     */
    BANK_CARD,
    
    /**
     * 姓名脱敏
     * 示例: 张**
     */
    NAME,
    
    /**
     * 地址脱敏
     * 示例: 北京市朝阳区****
     */
    ADDRESS,
    
    /**
     * 默认脱敏
     * 保留前3位和后4位，中间用*代替
     */
    DEFAULT
}
