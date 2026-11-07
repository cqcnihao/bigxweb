package com.cqcnihao.bigx.common.util;

import cn.hutool.core.util.StrUtil;

/**
 * 数据脱敏工具类
 */
public class DesensitizeUtil {
    
    /**
     * 手机号脱敏
     * 示例: 13812345678 -> 138****5678
     */
    public static String desensitizeMobile(String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return mobile;
        }
        if (mobile.length() < 7) {
            return mobile;
        }
        // 保留前3位和后4位
        return mobile.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }
    
    /**
     * 邮箱脱敏
     * 示例: test@example.com -> te****@example.com
     */
    public static String desensitizeEmail(String email) {
        if (StrUtil.isBlank(email)) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email;
        }
        // 保留前2位和@后面的内容
        return email.substring(0, 2) + "****" + email.substring(atIndex);
    }
    
    /**
     * 身份证号脱敏
     * 示例: 110101199001011234 -> 110***********1234
     */
    public static String desensitizeIdCard(String idCard) {
        if (StrUtil.isBlank(idCard)) {
            return idCard;
        }
        if (idCard.length() < 8) {
            return idCard;
        }
        // 保留前3位和后4位
        int length = idCard.length();
        return idCard.substring(0, 3) + 
               StrUtil.repeat('*', length - 7) + 
               idCard.substring(length - 4);
    }
    
    /**
     * 银行卡号脱敏
     * 示例: 6222000012345678 -> 6222 **** **** 5678
     */
    public static String desensitizeBankCard(String bankCard) {
        if (StrUtil.isBlank(bankCard)) {
            return bankCard;
        }
        if (bankCard.length() < 8) {
            return bankCard;
        }
        // 保留前4位和后4位，中间每4位加空格
        String prefix = bankCard.substring(0, 4);
        String suffix = bankCard.substring(bankCard.length() - 4);
        int middleLength = bankCard.length() - 8;
        StringBuilder middle = new StringBuilder();
        for (int i = 0; i < middleLength; i++) {
            if (i > 0 && i % 4 == 0) {
                middle.append(" ");
            }
            middle.append("*");
        }
        return prefix + " " + middle.toString() + " " + suffix;
    }
    
    /**
     * 姓名脱敏
     * 示例: 张三 -> 张*, 张三丰 -> 张*丰
     */
    public static String desensitizeName(String name) {
        if (StrUtil.isBlank(name)) {
            return name;
        }
        if (name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            // 两个字：保留姓
            return name.charAt(0) + "*";
        }
        // 三个字及以上：保留首尾
        return name.charAt(0) + 
               StrUtil.repeat('*', name.length() - 2) + 
               name.charAt(name.length() - 1);
    }
    
    /**
     * 地址脱敏
     * 示例: 北京市朝阳区建国路88号 -> 北京市朝阳区****
     */
    public static String desensitizeAddress(String address) {
        if (StrUtil.isBlank(address)) {
            return address;
        }
        if (address.length() <= 6) {
            return address;
        }
        // 保留前6个字符
        return address.substring(0, Math.min(6, address.length())) + "****";
    }
    
    /**
     * 默认脱敏
     * 保留前3位和后4位，中间用*代替
     */
    public static String desensitizeDefault(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        if (value.length() <= 7) {
            return value;
        }
        int length = value.length();
        return value.substring(0, 3) + 
               StrUtil.repeat('*', length - 7) + 
               value.substring(length - 4);
    }
}
