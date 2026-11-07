package com.cqcnihao.bigx.utils.mybatis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 读取项目相关配置
 *
 * @author yueyoubang
 */
@Component
@ConfigurationProperties(prefix = "yueyoubang")
public class YueYouBangConfig {

    private static List<String> dynamicTableList =
            List.of("wechat_account_follow_record",
                    "wechat_coin_record",
                    "wechat_daily_attendance",
                    "wechat_article",
                    "wechat_article_read_record");

    public static List<String> getDynamicTableList() {
        return dynamicTableList;
    }

    public void setDynamicTableList(List<String> dynamicTableList) {
        YueYouBangConfig.dynamicTableList = dynamicTableList;
    }

}


/***

 yueyoubang:
 # 动态表
 dynamicTableList:
 - wechat_account_follow_record
 - wechat_coin_record
 - wechat_daily_attendance
 - wechat_article
 - wechat_article_read_record
 *
 */