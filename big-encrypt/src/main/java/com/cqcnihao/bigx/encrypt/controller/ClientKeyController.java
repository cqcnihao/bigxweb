package com.cqcnihao.bigx.encrypt.controller;

import com.cqcnihao.bigx.common.IResult;
import com.cqcnihao.bigx.encrypt.annotation.SmPlain;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 客户端 SM2 公钥登记端点（严格验签的配套能力）
 * <p>
 * <b>为什么必须有这个端点：</b>「请求必须携带合法签名」在浏览器场景下的硬前提是
 * 客户端要有一把自己的 SM2 私钥。浏览器不可能持有服务端私钥，若把服务端私钥下发到前端，
 * 等于把签名能力交给所有人，验签彻底失效。正确链路是：
 * <ol>
 *   <li>客户端启动时在本地生成 SM2 密钥对，<b>私钥留在本地</b>（localStorage）；</li>
 *   <li>客户端调用本接口登记<b>公钥</b>，服务端返回随机签发的 {@code clientKeyId}；</li>
 *   <li>后续加密请求携带 {@code clientKeyId} 与本地私钥生成的签名，服务端据此取公钥验签。</li>
 * </ol>
 * <p>
 * <b>为什么标注 {@link SmPlain}：</b>首次登记时客户端还没有 {@code clientKeyId}，
 * 若该端点也要求信封加密 + 签名，就会陷入「要验签必须先登记、要登记必须先验签」的死循环。
 * 公钥本身不是秘密，明文传输不降低安全性；真正的机密性由后续业务报文的 SM4 信封承担。
 * <p>
 * <b>安全边界：</b>登记公钥只证明「请求方持有对应私钥」，<b>不等于身份认证</b>。
 * 身份仍由 Sa-Token 登录态承担；{@code clientKeyId} 是服务端随机生成的 128bit 标识，
 * 不接受客户端指定，避免攻击者用他人 id 覆盖公钥。
 *
 * @author bigweb
 */
@SmPlain
@RestController
@ConditionalOnExpression("'${big.encrypt.enabled:true}' == 'true'"
        + " and '${big.encrypt.sm2.client-key-enabled:true}' == 'true'")
@RequestMapping("${big.encrypt.sm2.client-key-endpoint-path:/crypto/sm2/client-key}")
public final class ClientKeyController {

    private final SmCryptoService cryptoService;

    public ClientKeyController(SmCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * 登记客户端公钥
     *
     * @param body {@code {"publicKey":"04xxxx..."}}，裸公钥点 hex 或 X.509 Base64 均可
     * @return {@code {"clientKeyId":"9f3c...","expiresIn":86400}}
     */
    @PostMapping
    public IResult<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String publicKey = body == null ? null : body.get("publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            throw new CryptoException(CryptoException.CODE_KEY_MISSING, "缺少 publicKey 字段");
        }
        // 同时下发有效期，客户端据此在过期前主动续期；
        // 否则公钥静默过期后，客户端仍在签名，服务端却查不到公钥，所有加密请求都会失败
        return IResult.success(Map.of(
                "clientKeyId", cryptoService.registerClientKey(publicKey),
                "expiresIn", cryptoService.getClientKeyTtlSeconds()));
    }

    /**
     * 注销客户端公钥（登出 / 更换设备时调用）
     */
    @DeleteMapping("/{clientKeyId}")
    public IResult<Void> revoke(@PathVariable String clientKeyId) {
        cryptoService.revokeClientKey(clientKeyId);
        return IResult.success();
    }
}