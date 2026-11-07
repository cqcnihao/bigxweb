package com.cqcnihao.bigx.encrypt.advice;

import com.cqcnihao.bigx.common.IResult;
import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.EncryptOrders;
import com.cqcnihao.bigx.encrypt.config.EncryptProperties;
import com.cqcnihao.bigx.encrypt.envelope.EncryptedPayload;
import com.cqcnihao.bigx.encrypt.exception.CryptoException;
import com.cqcnihao.bigx.common.serializer.DesensitizeContext;
import com.cqcnihao.bigx.encrypt.service.MessageCryptoService;
import com.cqcnihao.bigx.encrypt.support.EncryptContext;
import com.cqcnihao.bigx.encrypt.support.EncryptPolicy;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;

/**
 * 响应体国密加密（ResponseBodyAdvice）
 * <p>
 * <b>为什么必须是 Advice 而不是 HandlerInterceptor：</b>
 * <br>
 * {@code HandlerInterceptor#postHandle} 执行时机在 Controller 返回之后、
 * 但<b>在消息转换器把对象序列化成 JSON 之前</b>，此时拿到的是 {@code ModelAndView} 或
 * 尚未序列化的返回对象，而且方法签名里没有 OutputStream，
 * 想改响应体只能自己包一层 {@code HttpServletResponseWrapper} 劫持输出流——
 * 那样会破坏 SSE、文件下载、分块传输，还得处理字符集和 Content-Length，得不偿失。
 * <br>
 * {@code ResponseBodyAdvice#beforeBodyWrite} 则精确卡在「对象已序列化完成、尚未写回客户端」
 * 的钩子上，直接替换 body 即可，天然兼容 Spring 的消息转换体系。
 * <p>
 * <b>执行顺序（关键）：</b>本 Advice 使用 {@code LOWEST_PRECEDENCE - 100}，
 * 必须排在 {@code ResultResponseAdvice}（把裸对象包装成 Result）<b>之后</b>执行，
 * 否则先加密再被包装一层，前端就解不出来了。
 * <p>
 * <b>只加密 data，保留 code/message 明文</b>：前端无需解密即可判断业务成败。
 *
 * @author bigweb
 */
@Slf4j
@ControllerAdvice
@ConditionalOnProperty(prefix = "big.encrypt", name = "enabled", havingValue = "true", matchIfMissing = true)
@Order(EncryptOrders.ENCRYPT_RESPONSE)
public class SmEncryptResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final EncryptProperties properties;
    private final MessageCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public SmEncryptResponseBodyAdvice(EncryptProperties properties,
                                       MessageCryptoService cryptoService,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 只处理 JSON 转换器：String / Resource / byte[]（文件下载、SSE）一律放行，
        // 流式响应不经过完整 body，硬套加密会破坏流协议
        boolean isJackson = AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)
                || AbstractJacksonHttpMessageConverter.class.isAssignableFrom(converterType);
        if (!isJackson) {
            return false;
        }
        Method method = returnType.getMethod();
        if (method == null) {
            return false;
        }
        HttpServletRequest request = EncryptContext.currentRequest();
        if (EncryptContext.isSkipped(request)) {
            return false;
        }
        return policy(returnType).isEncrypt();
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return body;
        }
        if (selectedContentType != null
                && !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        // 已经加密过（例如外层又包了一层 advice），不再重复加密
        if (body instanceof EncryptedPayload) {
            return body;
        }

        EncryptPolicy.Decision decision = policy(returnType);
        String uri = request == null ? null : request.getURI().getPath();
        try {
            if (body instanceof IResult<?> result) {
                Object data = result.getData();
                if (data == null) {
                    // 无敏感数据可加密，保持明文，前端无需特判
                    return body;
                }
                if (data instanceof EncryptedPayload) {
                    return body;
                }
                String json = serialize(data);
                EncryptedPayload payload = cryptoService.encrypt(json, decision.getAlg(), decision.getKeyId());
                return wrap(result, payload);
            }

            // 兜底：返回值不是 IResult（说明本应用没有启用 ResultResponseAdvice），
            // 自己包装一层，保证输出结构与上面一致
            String json = (body instanceof CharSequence) ? body.toString() : serialize(body);
            EncryptedPayload payload = cryptoService.encrypt(json, decision.getAlg(), decision.getKeyId());
            Result<Object> out = new Result<>();
            out.setSuccess(true);
            out.setCode("200");
            out.setMessage("success");
            out.setTimestamp(System.currentTimeMillis());
            out.setData(payload);
            return out;
        } catch (Exception e) {
            // fail-close：宁可接口报错，也不静默降级为明文泄露数据
            log.error("响应加密失败, uri={}", uri, e);
            throw new CryptoException(CryptoException.CODE_ENCRYPT_FAILED, "响应加密失败", e);
        }
    }

    /**
     * 序列化待加密的 data
     * <p>
     * 开启 {@code big.encrypt.bypass-desensitize}（默认）时，序列化期间临时跳过
     * {@code @Desensitize}：data 马上就会被整体加密，密文已保护数据，
     * 先脱敏只会让客户端解密后拿不到完整明文。
     */
    private String serialize(Object value) throws Exception {
        if (properties.isBypassDesensitize()) {
            return DesensitizeContext.runWithoutDesensitize(() -> objectMapper.writeValueAsString(value));
        }
        return objectMapper.writeValueAsString(value);
    }

    private static Result<Object> wrap(IResult<?> src, EncryptedPayload payload) {
        Result<Object> out = new Result<>();
        out.setSuccess(src.isSuccess());
        out.setCode(src.getCode());
        out.setMessage(src.getMessage());
        out.setTimestamp(src.getTimestamp() == 0 ? System.currentTimeMillis() : src.getTimestamp());
        out.setData(payload);
        return out;
    }

    private EncryptPolicy.Decision policy(MethodParameter returnType) {
        HttpServletRequest request = EncryptContext.currentRequest();
        Method method = returnType.getMethod();
        Object cached = request == null ? null : request.getAttribute(EncryptContext.ATTR_POLICY);
        // 只有缓存条目对应的方法与当前返回方法一致时才复用，
        // 否则异常响应的 @ExceptionHandler 方法会误用原 Controller 方法的策略
        if (cached instanceof EncryptPolicy.PolicyEntry entry && entry.getMethod() == method) {
            return entry.getDecision();
        }
        EncryptPolicy.Decision d = EncryptPolicy.resolve(
                method,
                returnType.getContainingClass(),
                properties,
                EncryptContext.currentUri());
        if (request != null) {
            request.setAttribute(EncryptContext.ATTR_POLICY, new EncryptPolicy.PolicyEntry(method, d));
        }
        return d;
    }
}