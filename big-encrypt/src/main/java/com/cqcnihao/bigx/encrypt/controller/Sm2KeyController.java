package com.cqcnihao.bigx.encrypt.controller;

import com.cqcnihao.bigx.common.IResult;
import com.cqcnihao.bigx.encrypt.annotation.SmPlain;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SM2 公钥下发端点（混合加密握手第一步）
 * <p>
 * 客户端流程：先调本接口拿 SM2 公钥 → 本地生成随机 SM4 会话密钥 →
 * 用 SM2 公钥加密会话密钥回传 → 后续业务报文用会话密钥做 SM4 加解密。
 * 这样 SM2 只承担「保护短小会话密钥」的职责，业务报文仍由 SM4 承担，性能不受影响。
 * <p>
 * <b>关于「不允许其他模块再次声明」——本类是本端点的唯一声明处，三重保障：</b>
 * <ol>
 *   <li><b>本类不允许被继承</b>：{@code final}，其它模块无法通过子类再声明一个。</li>
 *   <li><b>组件扫描不会失控</b>：{@code @RestController} 是映射必需的
 *       （{@code RequestMappingHandlerMapping#isHandler} 只认 {@code @Controller}），
 *       所以确实会被扫描 {@code com.cqcnihao} 的模块（如 webYtuio）扫到；
 *       为此类上加了 {@link ConditionalOnExpression}——<b>组件扫描阶段同样会求值
 *       {@code @Conditional}</b>，因此 {@code big.encrypt.enabled=false} 时扫描也不会注册它；
 *       而扫描注册出来的 Bean 会让自动装配里的 {@code @ConditionalOnMissingBean} 自动退让，
 *       最终上下文中<b>始终只有一个实例、一份映射</b>。</li>
 *   <li><b>其它模块若自行声明同类型 Bean</b>：自动装配的
 *       {@code @ConditionalOnMissingBean} 退让，以业务模块的声明为准，不会重复映射。</li>
 * </ol>
 * 换言之：其它模块<b>不需要、也不应该</b>再写一份这个接口；
 * 若确实要用，请直接注入本类或改用 {@code big.encrypt.sm2.key-endpoint-path} 调整路径，
 * 千万不要另建一个映射到同一路径的 Controller（会导致 Spring 启动期 ambiguous mapping 报错）。
 * <p>
 * 另外标注了 {@link SmPlain}：保证即使在 {@code big.encrypt.mode=GLOBAL} 全局加密模式下，
 * 公钥响应依然保持明文，否则客户端永远拿不到公钥去完成握手。
 *
 * @author bigweb
 */
@SmPlain
@RestController
@ConditionalOnExpression("'${big.encrypt.enabled:true}' == 'true'"
        + " and '${big.encrypt.sm2.expose-key-endpoint:true}' == 'true'")
@RequestMapping("${big.encrypt.sm2.key-endpoint-path:/crypto/sm2/public-key}")
public final class Sm2KeyController {

    private final SmCryptoService cryptoService;

    public Sm2KeyController(SmCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * 下发 SM2 公钥
     *
     * @return {@code {"keyId":"v1","publicKey":"MFkwEw..."}}
     */
    @GetMapping
    public IResult<Map<String, String>> pubKey() {
        return IResult.success(Map.of(
                "keyId", cryptoService.sm2KeyId(),
                "publicKey", cryptoService.sm2PublicKey()));
    }
}
