package com.cqcnihao.bigx.encrypt.it;

import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.core.Sm4;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 响应签名端到端验证：开启 {@code big.encrypt.sign-response=true} 后，
 * 响应信封必须携带可被客户端公钥验签通过的 {@code sign}。
 * <p>
 * 这条链路与请求验签方向相反（服务端 JCA 签 → 浏览器 sm-crypto 验），
 * 签名原文必须是 {@code timestamp + "\n" + cipher}，任何一处不一致都会让前端 100% 验签失败，
 * 因此必须用真实 HTTP 响应做回归。
 */
@SpringBootTest(
        classes = ResponseSignIntegrationTest.TestApp.class,
        properties = "spring.autoconfigure.exclude=" +
                "com.cqcnihao.bigx.common.auth.BigAuthAutoConfiguration," +
                "cn.dev33.satoken.spring.SaBeanRegister," +
                "cn.dev33.satoken.spring.SaBeanInject," +
                "cn.dev33.satoken.spring.sso.SaSsoBeanRegister," +
                "cn.dev33.satoken.spring.sso.SaSsoBeanInject," +
                "cn.dev33.satoken.spring.oauth2.SaOAuth2BeanRegister," +
                "cn.dev33.satoken.spring.oauth2.SaOAuth2BeanInject")
@AutoConfigureMockMvc
class ResponseSignIntegrationTest {

    private static final String KEY_ID = "v1";
    private static final String SM4_KEY = Sm4.generateKeyBase64();
    private static final String[] SM2_KP = Sm2.generateKeyPairBase64();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SmCryptoService cryptoService;

    @DynamicPropertySource
    static void cryptoProps(DynamicPropertyRegistry registry) {
        registry.add("big.encrypt.enabled", () -> "true");
        registry.add("big.encrypt.mode", () -> "ANNOTATION");
        registry.add("big.encrypt.sm4.default-key-id", () -> KEY_ID);
        registry.add("big.encrypt.sm4.keys[" + KEY_ID + "]", () -> SM4_KEY);
        registry.add("big.encrypt.sm4.mac-enabled", () -> "true");
        registry.add("big.encrypt.replay-check", () -> "true");
        registry.add("big.encrypt.sm2.public-key", () -> SM2_KP[0]);
        registry.add("big.encrypt.sm2.private-key", () -> SM2_KP[1]);
        // 验签关闭：本用例只关注响应签名，避免请求构造干扰
        registry.add("big.encrypt.sm2.verify-signature", () -> "false");
        registry.add("big.encrypt.sign-response", () -> "true");
    }

    @Test
    void encryptedResponse_shouldCarryVerifiableSign() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("mobile", "13800000000"));

        MvcResult result = mockMvc.perform(post("/it/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.get("encrypt").asBoolean(), "data 必须是密文信封");

        String sign = data.get("sign").asText();
        assertFalse(sign.isBlank(), "开启响应签名后必须携带 sign");

        // 签名原文必须与前端约定完全一致：timestamp + "\n" + cipher
        String raw = data.get("timestamp").asText() + "\n" + data.get("cipher").asText();
        assertTrue(Sm2.verify(raw.getBytes(StandardCharsets.UTF_8), sign, SM2_KP[0]),
                "响应签名必须能被下发给客户端的公钥验签通过");

        // 篡改密文后签名必须失效，否则「响应防篡改」只是装饰
        String tampered = data.get("timestamp").asText() + "\n" + data.get("cipher").asText() + "x";
        assertFalse(Sm2.verify(tampered.getBytes(StandardCharsets.UTF_8), sign, SM2_KP[0]),
                "密文被篡改后响应签名必须验签失败");
    }

    @Test
    void clientPublicKey_shouldMatchSigningPrivateKey() {
        // 客户端拿到的公钥与服务端签名私钥必须是同一对，否则前端验签永远不通过
        assertEquals(SM2_KP[0], cryptoService.sm2PublicKey());
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    @RequestMapping("/it")
    static class TestController {

        @PostMapping("/echo")
        @SmEncrypt(request = false)
        public Result<Map<String, Object>> echo(@RequestBody Map<String, Object> body) {
            return Result.success(Map.of("echo", body.get("mobile")));
        }
    }
}