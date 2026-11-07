package com.cqcnihao.bigx.utils.mybatis;

public class DynamicTableNameContext {

    /**
     * 动态表名后缀
     */
    private static final ThreadLocal<String> TABLE_NAME_SUFFIX = new ThreadLocal<>();

    /**
     * 设置表名后缀
     *
     * @param suffix 表名后缀
     */
    public static void setTableNameSuffix(String suffix) {
        TABLE_NAME_SUFFIX.set(suffix);
    }

    /**
     * 获取表名后缀
     *
     * @return 表名后缀
     */
    public static String getTableNameSuffix() {
        return TABLE_NAME_SUFFIX.get();
    }

    /**
     * 清空线程池变量
     */
    public static void clear() {
        TABLE_NAME_SUFFIX.remove();
    }

}
