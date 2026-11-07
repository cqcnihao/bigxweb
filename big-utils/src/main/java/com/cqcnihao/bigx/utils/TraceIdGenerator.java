package com.cqcnihao.bigx.utils;

import java.util.UUID;

public  class TraceIdGenerator {
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
