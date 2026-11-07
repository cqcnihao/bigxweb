package com.cqcnihao.bigx.utils.validator.message;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 错误信息国际化支持
 */
@Component
public class I18nMessageSource {

    @Autowired
    private MessageSource messageSource;

    public String getMessage(String code, Object[] args, Locale locale) {
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            return code;  // 找不到消息时返回编码
        }
    }

}

