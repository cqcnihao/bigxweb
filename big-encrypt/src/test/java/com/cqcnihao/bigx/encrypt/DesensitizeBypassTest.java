package com.cqcnihao.bigx.encrypt;

import com.cqcnihao.bigx.common.annotation.Desensitize;
import com.cqcnihao.bigx.common.enums.DesensitizeType;
import com.cqcnihao.bigx.common.serializer.DesensitizeContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 脱敏旁路：加密响应时跳过 @Desensitize，普通响应保持脱敏
 */
class DesensitizeBypassTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    static class Demo {
        @Desensitize(type = DesensitizeType.MOBILE)
        public String mobile = "13800000000";
    }

    @Test
    void normalSerializationShouldStillDesensitize() {
        assertEquals("{\"mobile\":\"138****0000\"}",
                objectMapper.writeValueAsString(new Demo()));
    }

    @Test
    void bypassShouldKeepRawValueAndClearAfterUse() throws Exception {
        String json = DesensitizeContext.runWithoutDesensitize(
                () -> objectMapper.writeValueAsString(new Demo()));

        assertEquals("{\"mobile\":\"13800000000\"}", json);
        // 开关只覆盖当次调用，不能污染同线程后续的普通响应
        assertFalse(DesensitizeContext.isBypass());
        assertEquals("{\"mobile\":\"138****0000\"}",
                objectMapper.writeValueAsString(new Demo()));
    }

    @Test
    void bypassShouldBeClearedEvenIfSerializationFails() {
        try {
            DesensitizeContext.runWithoutDesensitize(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (Exception ignored) {
            // 预期异常
        }
        assertFalse(DesensitizeContext.isBypass());
    }
}
