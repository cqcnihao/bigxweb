package com.cqcnihao.bigx.utils;

/**
 * 接口「人」：定义 A、B、C 三个行为
 * <p>
 * 任何实现类（如「男人」Man）都必须给出这三个方法的具体实现。
 *
 * @author bigweb
 */
public interface Person {

    /** 行为 A */
    void seta(String a);
    String geta();

    /** 行为 B */
    void setb(String b);
    String getb();

    /** 行为 C */
    void setc(String c);
    String getc();
}
