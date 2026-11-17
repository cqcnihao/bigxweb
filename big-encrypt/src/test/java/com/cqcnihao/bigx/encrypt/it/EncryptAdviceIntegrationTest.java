package com.cqcnihao.bigx.encrypt.it;

import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.encrypt.annotation.SmPlain;
import com.cqcnihao.bigx.encrypt.controller.Sm2KeyController;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.core.Sm4;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端验证：拦截器 + 两个 Advice 的完整链路
 * <p>
 * 覆盖点：
 * <ol>
 *   <li>密文请求 → RequestBodyAdvice 解密 → Controller 拿到明文对象</li>
 *   <li>Controller 返回值 → ResponseBodyAdvice 只加密 data，code/message 保持明文</li>
 *   <li>未标注 @SmEncrypt 的接口完全不受影响</li>
 * </ol>
 */
@SpringBootTest(
        classes = EncryptAdviceIntegrationTest.TestApp.class,
        properties = "spring.autoconfigure.exclude=" +
                "com.cqcnihao.bigx.common.auth.BigAuthAutoConfiguration," +
                "cn.dev33.satoken.spring.SaBeanRegister," +
                "cn.dev33.satoken.spring.SaBeanInject," +
                "cn.dev33.satoken.spring.sso.SaSsoBeanRegister," +
                "cn.dev33.satoken.spring.sso.SaSsoBeanInject," +
                "cn.dev33.satoken.spring.oauth2.SaOAuth2BeanRegister," +
                "cn.dev33.satoken.spring.oauth2.SaOAuth2BeanInject")
@AutoConfigureMockMvc
class EncryptAdviceIntegrationTest {

    private static final String KEY_ID = "v1";
    private static final String SM4_KEY = Sm4.generateKeyBase64();
    private static final String[] SM2_KP = Sm2.generateKeyPairBase64();
    private static final String SM2_PUBLIC_KEY = SM2_KP[0];

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SmCryptoService cryptoService;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void cryptoProps(DynamicPropertyRegistry registry) {
        registry.add("big.encrypt.enabled", () -> "true");
        registry.add("big.encrypt.mode", () -> "ANNOTATION");
        registry.add("big.encrypt.sm4.default-key-id", () -> KEY_ID);
        registry.add("big.encrypt.sm4.keys[" + KEY_ID + "]", () -> SM4_KEY);
        registry.add("big.encrypt.sm4.mac-enabled", () -> "true");
        registry.add("big.encrypt.replay-check", () -> "true");
        registry.add("big.encrypt.interceptor-enabled", () -> "true");
        registry.add("big.encrypt.sm2.public-key", () -> SM2_PUBLIC_KEY);
        // 验签默认开启（严格模式），测试需用配对私钥对信封签名
        registry.add("big.encrypt.sm2.private-key", () -> SM2_KP[1]);
        registry.add("big.encrypt.sm2.key-id", () -> "sm2-v1");
        // 公钥端点默认关闭（生产安全默认），测试显式开启
        registry.add("big.encrypt.sm2.expose-key-endpoint", () -> "true");
    }

    @Test
    void encryptedEndpoint_shouldDecryptRequestAndEncryptResponseData() throws Exception {
        // 1) 客户端：把业务 JSON 用 SM4 加密成信封，并用 SM2 私钥签名
        String bizJson = "{\"mobile\":\"13800000000\"}";
        EncryptedPayload reqEnv = cryptoService.encrypt(bizJson, null, null);

        long ts = System.currentTimeMillis();
        String nonce = java.util.UUID.randomUUID().toString();
        String sign = cryptoService.sign(cryptoService.signPayload(ts, nonce, reqEnv.getCipher()));

        String body = objectMapper.writeValueAsString(Map.of(
                "alg", reqEnv.getAlg(),
                "keyId", reqEnv.getKeyId(),
                "cipher", reqEnv.getCipher(),
                "mac", reqEnv.getMac(),
                "sign", sign,
                "timestamp", ts,
                "nonce", nonce));

        // 2) 服务端：解密请求、处理业务、加密响应
        MvcResult result = mockMvc.perform(post("/it/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());

        // 3) code/message 保持明文可读
        assertEquals("200", resp.get("code").asText());
        assertTrue(resp.get("success").asBoolean());

        // 4) data 是密文信封
        JsonNode data = resp.get("data");
        assertTrue(data.get("encrypt").asBoolean(), "data 必须标记为密文");
        assertEquals("SM4/CBC/PKCS7Padding", data.get("alg").asText());
        assertNotNull(data.get("mac"));
        // 未开启响应签名时，信封里不能出现 sign 字段（更不能是 "sign": null）
        assertFalse(data.has("sign"), "未开启响应签名时不得输出 sign 字段");

        // 5) 解密 data，确认 Controller 确实收到了解密后的明文
        String plain = cryptoService.decrypt(data.get("cipher").asText(),
                data.get("alg").asText(), data.get("keyId").asText(), data.get("mac").asText());
        JsonNode echoed = objectMapper.readTree(plain);
        assertEquals("13800000000", echoed.get("echo").asText());
    }

    /**
     * 严格验签语义的核心回归：开启 verify-signature 后，加密信封<b>必须</b>携带签名。
     * 缺失签名一律拒绝（CODE_SIGN_INVALID），绝不允许“无签名就放行”的宽松模式，
     * 否则攻击者只需删掉 sign 字段即可绕过签名校验。
     */
    @Test
    void encryptedRequestWithoutSign_shouldBeRejected() throws Exception {
        EncryptedPayload reqEnv = cryptoService.encrypt("{\"mobile\":\"13800000000\"}", null, null);
        long ts = System.currentTimeMillis();
        String nonce = java.util.UUID.randomUUID().toString();
        // 故意不带 sign
        String body = objectMapper.writeValueAsString(Map.of(
                "alg", reqEnv.getAlg(),
                "keyId", reqEnv.getKeyId(),
                "cipher", reqEnv.getCipher(),
                "mac", reqEnv.getMac(),
                "timestamp", ts,
                "nonce", nonce));

        MvcResult result = mockMvc.perform(post("/it/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CRY-4003", resp.get("code").asText(), "缺少签名必须以 CODE_SIGN_INVALID 拒绝");
        assertFalse(resp.get("success").asBoolean());
    }

    /**
     * 伪造签名同样必须被拒绝：签名值随机但格式合法时不能放行
     */
    @Test
    void encryptedRequestWithForgedSign_shouldBeRejected() throws Exception {
        EncryptedPayload reqEnv = cryptoService.encrypt("{\"mobile\":\"13800000000\"}", null, null);
        long ts = System.currentTimeMillis();
        String nonce = java.util.UUID.randomUUID().toString();
        // 用另一个密钥对签名：签名格式合法，但公钥与服务端配置不匹配
        String[] otherKp = Sm2.generateKeyPairBase64();
        String payload = cryptoService.signPayload(ts, nonce, reqEnv.getCipher());
        String forged = Sm2.sign(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), otherKp[1]);
        String body = objectMapper.writeValueAsString(Map.of(
                "alg", reqEnv.getAlg(),
                "keyId", reqEnv.getKeyId(),
                "cipher", reqEnv.getCipher(),
                "mac", reqEnv.getMac(),
                "sign", forged,
                "timestamp", ts,
                "nonce", nonce));

        MvcResult result = mockMvc.perform(post("/it/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("CRY-4003", resp.get("code").asText(), "签名不匹配必须以 CODE_SIGN_INVALID 拒绝");
        assertFalse(resp.get("success").asBoolean());
    }

    @Test
    void plainEndpoint_shouldNotBeEncrypted() throws Exception {
        MvcResult result = mockMvc.perform(post("/it/plain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobile\":\"13900000000\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = resp.get("data");
        assertFalse(data.isObject() && data.has("encrypt"), "未标注注解的接口不应被加密");
        assertEquals("plain:13900000000", data.asText());
    }

    /**
     * 异常响应必须保持明文：否则前端拿不到可读的 code/message，
     * 也无法区分「业务失败」和「解密失败」
     */
    @Test
    void errorResponse_shouldStayPlainText() throws Exception {
        EncryptedPayload reqEnv = cryptoService.encrypt("{\"mobile\":\"13800000000\"}", null, null);
        long ts = System.currentTimeMillis();
        String nonce = java.util.UUID.randomUUID().toString();
        String sign = cryptoService.sign(cryptoService.signPayload(ts, nonce, reqEnv.getCipher()));
        String body = objectMapper.writeValueAsString(Map.of(
                "alg", reqEnv.getAlg(),
                "keyId", reqEnv.getKeyId(),
                "cipher", reqEnv.getCipher(),
                "mac", reqEnv.getMac(),
                "sign", sign,
                "timestamp", ts,
                "nonce", nonce));

        MvcResult result = mockMvc.perform(post("/it/boom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = resp.get("data");

        // GlobalExceptionHandler 产出的响应不应被加密
        assertFalse(data.isObject() && data.has("encrypt"), "异常响应不应被加密");
        assertEquals("boom", resp.get("message").asText());
    }

    /**
     * SM2 公钥端点：必须明文返回，且客户端能直接拿去加密会话密钥
     */
    @Test
    void publicKeyEndpoint_shouldReturnPlainTextPublicKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/crypto/sm2/public-key"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("200", resp.get("code").asText());

        JsonNode data = resp.get("data");
        assertFalse(data.has("encrypt"), "公钥端点必须明文，否则客户端无法完成握手");
        assertEquals("sm2-v1", data.get("keyId").asText());
        assertEquals(SM2_PUBLIC_KEY, data.get("publicKey").asText());

        // 下发的公钥必须真的可用：能验签与之配对的私钥产生的签名
        byte[] sample = "handshake".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(Sm2.verify(sample, Sm2.sign(sample, SM2_KP[1]), data.get("publicKey").asText()));
    }

    /**
     * 约束：公钥端点只允许 big-Encrypt 声明一次，且不允许被继承或重复声明
     */
    @Test
    void sm2KeyController_shouldBeDeclaredOnlyOnce(@Autowired ApplicationContext ctx) {
        Class<?> c = Sm2KeyController.class;
        assertTrue(Modifier.isFinal(c.getModifiers()), "必须为 final，不允许被继承后再声明");
        assertTrue(c.isAnnotationPresent(SmPlain.class), "公钥端点必须豁免加密");
        assertTrue(c.isAnnotationPresent(RestController.class), "映射需要 @Controller 体系");
        assertTrue(c.isAnnotationPresent(ConditionalOnExpression.class),
                "必须带类级条件，否则扫描 com.cqcnihao 的模块会绕过开关直接注册它");
        assertEquals(1, ctx.getBeansOfType(Sm2KeyController.class).size(),
                "上下文中只允许存在一个 Sm2KeyController 实例");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        com.cqcnihao.bigx.common.GlobalExceptionHandler globalExceptionHandler() {
            return new com.cqcnihao.bigx.common.GlobalExceptionHandler();
        }
    }

    @RestController
    @RequestMapping("/it")
    static class TestController {

        @PostMapping("/echo")
        @SmEncrypt
        public Result<Map<String, Object>> echo(@RequestBody Map<String, Object> body) {
            // 这里拿到的必须是解密后的明文对象
            return Result.success(Map.of("echo", body.get("mobile")));
        }

        @PostMapping("/plain")
        public Result<String> plain(@RequestBody Map<String, Object> body) {
            return Result.success("plain:" + body.get("mobile"));
        }

        @PostMapping("/boom")
        @SmEncrypt
        public Result<String> boom(@RequestBody Map<String, Object> body) {
            throw new com.cqcnihao.bigx.core.exception.BusinessException("500", "boom");
        }
    }
}