package com.cqcnihao.bigx.encrypt.support;

/**
 * 客户端 SM2 公钥存储
 * <p>
 * <b>为什么需要它：</b>「请求必须携带合法签名」在浏览器场景下有一个硬前提——
 * 浏览器不可能持有服务端私钥。若强行让前端用服务端密钥签名，等于把私钥下发到每个终端，
 * 签名体系直接失效。正确做法是让客户端<b>本地生成</b> SM2 密钥对：
 * 私钥留在客户端（localStorage），公钥登记到服务端，服务端按 {@code clientKeyId} 取公钥验签。
 * <p>
 * 安全边界：登记公钥只能证明「请求方持有对应私钥」，<b>不等于身份认证</b>；
 * 身份仍由 Sa-Token 登录态承担，两者互补。因此登记端点允许匿名访问，
 * 但 {@code clientKeyId} 必须由服务端签发，杜绝客户端指定 id 覆盖他人公钥。
 *
 * @author bigweb
 */
public interface ClientKeyStore {

    /**
     * 保存客户端公钥
     *
     * @param clientKeyId   服务端签发的密钥标识
     * @param publicKey     X.509 DER 的 Base64（已归一化）
     * @param ttlSeconds    存活秒数
     */
    void save(String clientKeyId, String publicKey, long ttlSeconds);

    /**
     * 查询客户端公钥
     *
     * @param clientKeyId 密钥标识
     * @return X.509 Base64；不存在或已过期返回 {@code null}
     */
    String find(String clientKeyId);

    /** 主动注销（客户端登出时可调用） */
    void remove(String clientKeyId);
}