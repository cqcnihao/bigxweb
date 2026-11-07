package com.cqcnihao.bigx.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应结果单元测试
 */
class ResultTest {

    @Test
    void testSuccessWithoutData() {
        Result<Void> result = Result.success();
        assertTrue(result.isSuccess());
        assertEquals("200", result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertNull(result.getData());
        assertTrue(result.getTimestamp() > 0);
    }

    @Test
    void testSuccessWithData() {
        String data = "test-data";
        Result<String> result = Result.success(data);
        assertTrue(result.isSuccess());
        assertEquals("200", result.getCode());
        assertEquals(data, result.getData());
    }

    @Test
    void testSuccessWithMessageAndData() {
        Result<String> result = Result.success("自定义消息", "test-data");
        assertTrue(result.isSuccess());
        assertEquals("自定义消息", result.getMessage());
        assertEquals("test-data", result.getData());
    }

    @Test
    void testErrorDefault() {
        Result<Void> result = Result.error();
        assertFalse(result.isSuccess());
        assertEquals("500", result.getCode());
        assertEquals("操作失败", result.getMessage());
    }

    @Test
    void testErrorWithMessage() {
        Result<Void> result = Result.error("业务异常");
        assertFalse(result.isSuccess());
        assertEquals("500", result.getCode());
        assertEquals("业务异常", result.getMessage());
    }

    @Test
    void testErrorWithCodeAndMessage() {
        Result<Void> result = Result.error("404", "资源不存在");
        assertFalse(result.isSuccess());
        assertEquals("404", result.getCode());
        assertEquals("资源不存在", result.getMessage());
    }

}
