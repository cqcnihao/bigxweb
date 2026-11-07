package com.cqcnihao.bigx.encrypt;

import org.springframework.core.Ordered;

/**
 * big-Encrypt 各组件的执行顺序契约
 * <p>
 * Spring MVC 中多个 {@code ResponseBodyAdvice} 按 order <b>升序</b>依次执行，
 * 前一个的输出作为后一个的输入，所以顺序错了会直接导致前端解不开密文。
 * <p>
 * 完整链路（数字越小越先执行）：
 * <pre>
 *   1. RESULT_WRAPPER   统一返回包装  裸对象 -&gt; Result
 *   2. ENCRYPT_RESPONSE 响应加密     只加密 Result.data
 * </pre>
 * 必须保证 {@code RESULT_WRAPPER < ENCRYPT_RESPONSE}，否则会先加密再被包一层，
 * 前端拿到 {@code Result&lt;Result&lt;密文&gt;&gt;} 而解不出业务数据。
 * <p>
 * 请求解密是 {@code RequestBodyAdvice}，与 {@code ResponseBodyAdvice} 分属两条链，
 * 不存在上述冲突，取最靠前的值以确保只早于参数绑定即可。
 *
 * @author bigweb
 */
public final class EncryptOrders {

    /**
     * 统一返回包装 advice 的顺序
     * <p>
     * 与 {@code big-web-common} 的 {@code ResultResponseAdvice} 保持一致：
     * 若日后调整，两边必须同步修改。
     */
    public static final int RESULT_WRAPPER = Ordered.HIGHEST_PRECEDENCE + 100;

    /** 请求体解密：只需早于参数绑定 */
    public static final int DECRYPT_REQUEST = Ordered.HIGHEST_PRECEDENCE + 100;

    /** 响应体加密：必须晚于 {@link #RESULT_WRAPPER} */
    public static final int ENCRYPT_RESPONSE = Ordered.LOWEST_PRECEDENCE - 100;

    private EncryptOrders() {
    }
}
