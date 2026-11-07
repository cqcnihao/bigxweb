package com.cqcnihao.bigx.encrypt.it;

import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.core.Sm2;
import com.cqcnihao.bigx.encrypt.core.Sm4;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局加密模式（{@code big.encrypt.mode=GLOBAL}）下 SM2 公钥端点的行为验证
 * <p>
 * 这是 {@code @SmPlain} 存在的核心理由：全局模式下所有接口都会被加密，
 * 但公钥端点必须例外——否则客户端永远拿不到公钥，混合加密握手无法开始。
 */
@SpringBootTest(
        classes = Sm2KeyEndpointGlobalModeTest.TestApp.class,
        properties = "spring.autoconfigure.exclude=" +
                "com.cqcnihao.bigx.common.auth.BigAuthAutoConfiguration," +
                "cn.dev33.satoken.spring.SaBeanRegister," +
                "cn.dev33.satoken.spring.SaBeanInject," +
                "cn.dev33.satoken.spring.sso.SaSsoBeanRegister," +
                "cn.dev33.satoken.spring.sso.SaSsoBeanInject," +
                "cn.dev33.satoken.spring.oauth2.SaOAuth2BeanRegister," +
                "cn.dev33.satoken.spring.oauth2.SaOAuth2BeanInject")
@AutoConfigureMockMvc
class Sm2KeyEndpointGlobalModeTest {

    private static final String SM4_KEY = Sm4.generateKeyBase64();
    private static final String[] SM2_KP = Sm2.generateKeyPairBase64();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SmCryptoService cryptoService;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void cryptoProps(DynamicPropertyRegistry registry) {
        registry.add("big.encrypt.enabled", () -> "true");
        registry.add("big.encrypt.mode", () -> "GLOBAL");
        registry.add("big.encrypt.sm4.default-key-id", () -> "v1");
        registry.add("big.encrypt.sm4.keys[v1]", () -> SM4_KEY);
        registry.add("big.encrypt.sm4.mac-enabled", () -> "true");
        registry.add("big.encrypt.sm2.public-key", () -> SM2_KP[0]);
    }

    @Test
    void publicKeyEndpoint_shouldBeMappedOnlyOnce(@Autowired org.springframework.context.ApplicationContext ctx) {
        var mapping = ctx.getBean(RequestMappingHandlerMapping.class);
        long count = mapping.getHandlerMethods().keySet().stream()
                .filter(i -> i.toString().contains("/crypto/sm2/public-key"))
                .count();
        org.junit.jupiter.api.Assertions.assertEquals(1, count, "公钥端点只允许被映射一次");
    }

    @Test
    void publicKeyEndpoint_shouldStayPlainInGlobalMode() throws Exception {
        MvcResult result = mockMvc.perform(get("/crypto/sm2/public-key"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = resp.get("data");

        assertFalse(data.has("encrypt"), "全局模式下公钥端点也必须保持明文");
        assertEquals(SM2_KP[0], data.get("publicKey").asText());
    }

    @Test
    void normalEndpoint_shouldBeEncryptedInGlobalMode() throws Exception {
        // 全局模式下请求体也必须是密文信封
        EncryptedPayload env = cryptoService.encrypt("{\"m\":\"1\"}", null, null);
        String body = objectMapper.writeValueAsString(Map.of(
                "alg", env.getAlg(),
                "keyId", env.getKeyId(),
                "cipher", env.getCipher(),
                "mac", env.getMac(),
                "timestamp", System.currentTimeMillis(),
                "nonce", UUID.randomUUID().toString()));

        MvcResult result = mockMvc.perform(post("/gm/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertTrue(data.has("encrypt") && data.get("encrypt").asBoolean(),
                "全局模式下普通接口必须被加密");
    }

    @EnableAutoConfiguration
    @Configuration
    static class TestApp {

        @Bean
        EchoController echoController() {
            return new EchoController();
        }
    }

    @RestController
    @RequestMapping("/gm")
    static class EchoController {

        @PostMapping("/echo")
        public Result<Map<String, Object>> echo(@RequestBody Map<String, Object> body) {
            return Result.success(Map.of("echo", body.get("m")));
        }
    }
}