package com.cqcnihao.bigx.utils;

/**
 * 「男人」：接口「人」的一个实现类
 * <p>
 * Person 接口定义了 A、B、C 三个值（seta/geta、setb/getb、setc/getc），
 * 本实现以三个私有字段持有这些值，默认值为「男人执行行为 A/B/C」，
 * 可通过接口 setter 或 {@link ManUtil} 工具类修改。
 *
 * @author bigweb
 */
public class Man implements Person {

    /** 值 A */
    private String a = "男人执行行为 A";

    /** 值 B */
    private String b = "男人执行行为 B";

    /** 值 C */
    private String c = "男人执行行为 C";

    @Override
    public void seta(String a) {
        this.a = a;
    }

    @Override
    public String geta() {
        return a;
    }

    @Override
    public void setb(String b) {
        this.b = b;
    }

    @Override
    public String getb() {
        return b;
    }

    @Override
    public void setc(String c) {
        this.c = c;
    }

    @Override
    public String getc() {
        return c;
    }
}
