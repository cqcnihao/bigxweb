package com.cqcnihao.bigx.common.serializer;

/**
 * 脱敏旁路开关
 * <p>
 * 响应加密场景下，data 序列化完成后会被整体加密；此时若仍按 {@code @Desensitize}
 * 先脱敏再加密，客户端解密后也只能拿到脱敏值，属于「双重保护 + 信息丢失」。
 * 因此加密路径上用本开关临时跳过脱敏，让密文里保留完整明文，解密后由客户端按需展示。
 * <p>
 * 实现为 ThreadLocal，仅覆盖当次序列化调用，不污染同线程后续的普通响应。
 *
 * @author bigweb
 */
public final class DesensitizeContext {

    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();

    private DesensitizeContext() {
    }

    /** 当前线程是否处于「跳过脱敏」状态 */
    public static boolean isBypass() {
        return Boolean.TRUE.equals(BYPASS.get());
    }

    /**
     * 在跳过脱敏的状态下执行序列化，结束后恢复原状态
     *
     * @param action 序列化动作
     */
    public static <T> T runWithoutDesensitize(Action<T> action) throws Exception {
        Boolean previous = BYPASS.get();
        BYPASS.set(Boolean.TRUE);
        try {
            return action.run();
        } finally {
            if (previous == null) {
                BYPASS.remove();
            } else {
                BYPASS.set(previous);
            }
        }
    }

    @FunctionalInterface
    public interface Action<T> {
        T run() throws Exception;
    }
}
