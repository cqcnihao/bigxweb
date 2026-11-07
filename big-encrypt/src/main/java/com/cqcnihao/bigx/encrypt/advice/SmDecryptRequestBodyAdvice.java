package com.cqcnihao.bigx.encrypt.advice;

import com.cqcnihao.bigx.encrypt.EncryptOrders;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedRequest;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import com.cqcnihao.bigx.encrypt.service.MessageCryptoService;
import com.cqcnihao.bigx.encrypt.support.EncryptContext;
import com.cqcnihao.bigx.encrypt.support.EncryptPolicy;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * 请求体国密解密（RequestBodyAdvice）
 * <p>
 * <b>为什么必须是 Advice 而不是 HandlerInterceptor：</b>
 * <ol>
 *   <li>{@code HttpServletRequest#getInputStream()} 只能读一次，拦截器读走之后
 *       参数解析器就拿不到流了，除非自己包一层可重放的 RequestWrapper（脏且容易踩坑）；</li>
 *   <li>即使拦截器能读到 body，它也<b>改不了</b> {@code @RequestBody} 最终绑定出来的对象——
 *       参数绑定发生在 {@code HandlerMethodArgumentResolver} 里，拦截器在它之前执行，
 *       对解密结果无从注入。</li>
 * </ol>
 * 而 {@code RequestBodyAdvice#beforeBodyRead} 正好卡在「流被读取之后、Jackson 反序列化之前」，
 * 把密文字节换成明文字节即可，业务 Controller 拿到的就是解密后的对象，零侵入。
 * <p>
 * 处理顺序：验签(SM2) → MAC(SM3)+SM4 解密 → 新鲜性/防重放。
 * nonce 必须在真实性（验签/MAC）确认之后再消费，否则伪造请求可抢先烧掉合法 nonce。
 *
 * @author bigweb
 */
@Slf4j
@ControllerAdvice
@ConditionalOnProperty(prefix = "big.encrypt", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(EncryptOrders.DECRYPT_REQUEST)
public class SmDecryptRequestBodyAdvice implements RequestBodyAdvice {

    private final EncryptProperties properties;
    private final MessageCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public SmDecryptRequestBodyAdvice(EncryptProperties properties,
                                      MessageCryptoService cryptoService,
                                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        boolean isJackson = AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)
                || AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType);
        if (!isJackson) {
            return false;
        }
        // 只处理 @RequestBody 参数
        if (!methodParameter.hasParameterAnnotation(RequestBody.class)) {
            return false;
        }
        HttpServletRequest request = EncryptContext.currentRequest();
        if (EncryptContext.isSkipped(request)) {
            return false;
        }
        return policy(methodParameter).isDecrypt();
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                           Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) {
        HttpServletRequest request = EncryptContext.currentRequest();
        byte[] raw;
        try (InputStream in = inputMessage.getBody()) {
            raw = in.readAllBytes();
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_BAD_ENVELOPE, "读取请求体失败", e);
        }
        if (raw.length == 0) {
            return inputMessage;
        }

        EncryptPolicy.Decision decision = policy(parameter);
        try {
            EncryptedRequest envelope = parse(raw);
            if (!envelope.hasCipher()) {
                throw new CryptoException(CryptoException.CODE_BAD_ENVELOPE,
                        "请求体不是合法的密文信封，缺少 cipher 字段");
            }

            Long timestamp = firstNonNull(envelope.getTimestamp(), headerTimestamp(request));
            String nonce = firstNonBlank(envelope.getNonce(), header(request, EncryptContext.HEADER_NONCE));

            // 1) SM2 验签：签的是 timestamp|nonce|cipher，先证明真实性再解密
            if (decision.isSign()) {
                if (timestamp == null) {
                    // 缺时间戳的签名等价于“永不过期”，timestamp-validity 对其完全失效，直接拒绝
                    throw new CryptoException(CryptoException.CODE_TIMESTAMP_EXPIRED, "签名请求必须携带时间戳");
                }
                String sign = firstNonBlank(envelope.getSign(), header(request, EncryptContext.HEADER_SIGN));
                if (!StringUtils.hasText(sign)) {
                    throw new CryptoException(CryptoException.CODE_SIGN_INVALID, "缺少签名值");
                }
                String payload = cryptoService.signPayload(timestamp, nonce, envelope.getCipher());
                if (!cryptoService.verify(payload, sign)) {
                    throw new CryptoException(CryptoException.CODE_SIGN_INVALID, "SM2 签名校验失败");
                }
            }

            // 2) SM4 解密（内部先做 HMAC-SM3 完整性校验）
            String keyId = firstNonBlank(envelope.getKeyId(), header(request, EncryptContext.HEADER_KEY_ID));
            String plain = cryptoService.decrypt(
                    envelope.getCipher(),
                    envelope.getAlg(),
                    keyId,
                    envelope.getMac());

            // 3) 时间戳 + nonce 防重放：验签/MAC 全部通过后才消费 nonce，
            //    否则攻击者可用伪造请求抢先烧掉合法 nonce（拦截器若已消费则跳过）
            if (decision.isReplay() && !EncryptContext.isFreshnessDone(request)) {
                cryptoService.checkFreshness(timestamp, nonce);
                EncryptContext.markFreshnessDone(request);
            }

            if (log.isDebugEnabled()) {
                // 只记长度，绝不记明文
                log.debug("SM4 解密成功, uri={}, 明文长度={}", EncryptContext.currentUri(), plain.length());
            }
            return new DecryptedHttpInputMessage(inputMessage.getHeaders(),
                    plain.getBytes(StandardCharsets.UTF_8));
        } catch (CryptoException e) {
            log.warn("请求解密失败, uri={}, code={}, msg={}",
                    EncryptContext.currentUri(), e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("请求解密异常, uri={}", EncryptContext.currentUri(), e);
            throw new CryptoException(CryptoException.CODE_DECRYPT_FAILED, "请求解密失败", e);
        }
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                  Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private EncryptedRequest parse(byte[] raw) {
        try {
            return objectMapper.readValue(raw, EncryptedRequest.class);
        } catch (Exception e) {
            throw new CryptoException(CryptoException.CODE_BAD_ENVELOPE, "请求体不是合法 JSON 密文信封", e);
        }
    }

    private EncryptPolicy.Decision policy(MethodParameter parameter) {
        HttpServletRequest request = EncryptContext.currentRequest();
        Method method = parameter.getMethod();
        Object cached = request == null ? null : request.getAttribute(EncryptContext.ATTR_POLICY);
        if (cached instanceof EncryptPolicy.PolicyEntry entry && entry.getMethod() == method) {
            return entry.getDecision();
        }
        EncryptPolicy.Decision d = EncryptPolicy.resolve(
                method,
                parameter.getContainingClass(),
                properties,
                EncryptContext.currentUri());
        if (request != null) {
            request.setAttribute(EncryptContext.ATTR_POLICY, new EncryptPolicy.PolicyEntry(method, d));
        }
        return d;
    }

    private static Long headerTimestamp(HttpServletRequest request) {
        String v = header(request, EncryptContext.HEADER_TIMESTAMP);
        if (!StringUtils.hasText(v)) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new CryptoException(CryptoException.CODE_TIMESTAMP_EXPIRED, "时间戳格式非法");
        }
    }

    private static String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    private static Long firstNonNull(Long a, Long b) {
        return a != null ? a : b;
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a : (StringUtils.hasText(b) ? b : null);
    }

    /**
     * 用解密后的明文字节流替换原始请求流
     */
    private static class DecryptedHttpInputMessage implements HttpInputMessage {
        private final HttpHeaders headers;
        private final byte[] body;

        DecryptedHttpInputMessage(HttpHeaders headers, byte[] body) {
            this.headers = headers;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}