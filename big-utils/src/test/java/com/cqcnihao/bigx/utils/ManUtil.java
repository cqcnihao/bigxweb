package com.cqcnihao.bigx.utils;

import java.util.Objects;

/**
 * 「男人」工具类：修改 Person（男人）的 A、B、C 三个值
 * <p>
 * 典型工具类范式：{@code final} + 私有构造器 + 全静态方法，不可实例化、不可继承。
 * 方法参数统一使用<b>接口类型</b> {@link Person} 传递（面向接口编程）：
 * 不关心传入的是 Man 还是其他实现类，一律通过接口的 seta/geta 等方法读写值。
 *
 * @author bigweb
 */
public final class ManUtil {

    private ManUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 空白兜底：传入 null/空白时保持原值不变 */
    private static String normalize(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** 改变值 A */
    public static void changeA(Person person, String newValue) {
        Objects.requireNonNull(person, "person 不能为空");
        person.seta(normalize(newValue, person.geta()));
    }

    /** 改变值 B */
    public static void changeB(Person person, String newValue) {
        Objects.requireNonNull(person, "person 不能为空");
        person.setb(normalize(newValue, person.getb()));
    }

    /** 改变值 C */
    public static void changeC(Person person, String newValue) {
        Objects.requireNonNull(person, "person 不能为空");
        person.setc(normalize(newValue, person.getc()));
    }

    /** 一次性改变 A、B、C 三个值 */
    public static void changeAll(Person person, String newA, String newB, String newC) {
        changeA(person, newA);
        changeB(person, newB);
        changeC(person, newC);
    }

    /** 输出改变后的 A、B、C 三个值 */
    public static void printAll(Person person) {
        Objects.requireNonNull(person, "person 不能为空");
        System.out.println("A = " + person.geta());
        System.out.println("B = " + person.getb());
        System.out.println("C = " + person.getc());
    }

    /** 测试方法 */
    public static void main(String[] args) {
        Man man = new Man();
        man.seta("原 A");
        man.setb("原 B");
        man.setc(null);

        Person person = (Person) man;


        SSS(person);

        person.seta(person.getc());

        printAll(person);
    }



    private static void SSS(Person person) {
        person.setc(person.geta());

        person.seta("xxxx");
    }
}
