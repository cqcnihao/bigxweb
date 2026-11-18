package com.cqcnihao.bigx.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 「男人」测试类
 * <p>
 * 演示重点：<b>方法参数使用接口类型传递</b>（面向接口编程）——
 * {@code doBehavior(Person person)} 与 {@code ManUtil} 的全部方法都接收接口 {@link Person}，
 * 调用时传入实现类 {@link Man}，内部只依赖接口定义的 seta/geta 等方法，
 * 不关心具体实现是谁。
 *
 * @author bigweb
 */
@DisplayName("接口「人」与实现「男人」")
class ManTest {

    /** 方法参数使用接口类型传递：调用方传实现类，方法内按接口编程 */
    private void doBehavior(Person person) {
        person.seta("接口传递修改 A");
        person.setb("接口传递修改 B");
        person.setc("接口传递修改 C");
    }

    @Test
    @DisplayName("Man 实现类直接读写 A、B、C")
    void testManDirectCall() {
        Man man = new Man();
        assertEquals("男人执行行为 A", man.geta());
        assertEquals("男人执行行为 B", man.getb());
        assertEquals("男人执行行为 C", man.getc());

        man.seta("直接修改 A");
        assertEquals("直接修改 A", man.geta());
    }

    @Test
    @DisplayName("方法参数以接口 Person 类型传递 Man 实现")
    void testPassImplementationAsInterface() {
        // Man 实例向上转型为接口 Person，作为参数传递
        Person person = new Man();
        assertDoesNotThrow(() -> doBehavior(person));
        assertEquals("接口传递修改 A", person.geta());
        assertEquals("接口传递修改 B", person.getb());
        assertEquals("接口传递修改 C", person.getc());
    }

    @Test
    @DisplayName("ManUtil 工具类改变 A、B、C 三个值后生效")
    void testManUtilChangeValues() {
        Person man = new Man();

        // 单个改变
        ManUtil.changeA(man, "上班 A");
        assertEquals("上班 A", man.geta());

        // 一次性改变三个值
        ManUtil.changeAll(man, "吃饭 A", "睡觉 B", "打豆豆 C");
        assertEquals("吃饭 A", man.geta());
        assertEquals("睡觉 B", man.getb());
        assertEquals("打豆豆 C", man.getc());

        // 改变后的值经接口 getter 读回
        assertDoesNotThrow(() -> ManUtil.printAll(man));
    }

    @Test
    @DisplayName("ManUtil 传入 null/空白不覆盖原值，工具类不可实例化")
    void testManUtilGuard() {
        Person man = new Man();
        ManUtil.changeAll(man, null, "  ", "有效的 C");
        assertEquals("男人执行行为 A", man.geta());
        assertEquals("男人执行行为 B", man.getb());
        assertEquals("有效的 C", man.getc());

        // 私有构造器：反射调用应抛 UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            java.lang.reflect.Constructor<ManUtil> ctor = ManUtil.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            try {
                ctor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
