package com.cqcnihao.bigx.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResultUtil 响应工具类单元测试
 */
class ResultUtilTest {

    @Test
    void testOkWithoutData() {
        Result<Void> result = ResultUtil.ok();
        assertTrue(result.isSuccess());
        assertEquals("200", result.getCode());
    }

    @Test
    void testOkWithData() {
        Result<String> result = ResultUtil.ok("data");
        assertTrue(result.isSuccess());
        assertEquals("data", result.getData());
    }

    @Test
    void testOkWithMessageAndData() {
        Result<String> result = ResultUtil.ok("msg", "data");
        assertTrue(result.isSuccess());
        assertEquals("msg", result.getMessage());
        assertEquals("data", result.getData());
    }

    @Test
    void testFailDefault() {
        Result<Void> result = ResultUtil.fail();
        assertFalse(result.isSuccess());
        assertEquals("500", result.getCode());
    }

    @Test
    void testFailWithMessage() {
        Result<Void> result = ResultUtil.fail("失败");
        assertFalse(result.isSuccess());
        assertEquals("失败", result.getMessage());
    }

    @Test
    void testFailWithCodeAndMessage() {
        Result<Void> result = ResultUtil.fail("403", "禁止访问");
        assertFalse(result.isSuccess());
        assertEquals("403", result.getCode());
        assertEquals("禁止访问", result.getMessage());
    }

    @Test
    void testFailWithResultCode() {
        Result<Void> result = ResultUtil.fail(ResultCode.NOT_FOUND);
        assertFalse(result.isSuccess());
        assertEquals("404", result.getCode());
        assertEquals("资源不存在", result.getMessage());
    }
}
