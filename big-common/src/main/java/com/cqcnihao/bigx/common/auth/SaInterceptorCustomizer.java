package com.cqcnihao.bigx.common.auth;

import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpInterface;

import java.util.ArrayList;
import java.util.List;

public interface SaInterceptorCustomizer {

    void customize(RequestConfig config);

    @FunctionalInterface
    interface SaHandler {
        void handle(SaRouter router);
    }

    class RequestConfig {
        private boolean autoRegister = true;
        private boolean enableLoginCheck = true;
        private String[] includePatterns = new String[]{"/**"};
        private SaHandler handler;
        private final List<String> excludePatterns = new ArrayList<>(List.of(
                "/actuator/**",
                "/error",
                "/doc.html",
                "/v3/api-docs/**",
                "/v2/api-docs/**",
                "/swagger-ui/**",
                "/swagger-resources/**",
                "/webjars/**",
                "/favicon.ico"
        ));

        public boolean isAutoRegister() { return autoRegister; }
        public RequestConfig setAutoRegister(boolean autoRegister) { this.autoRegister = autoRegister; return this; }
        public boolean isEnableLoginCheck() { return enableLoginCheck; }
        public RequestConfig setEnableLoginCheck(boolean enableLoginCheck) { this.enableLoginCheck = enableLoginCheck; return this; }
        public String[] getIncludePatterns() { return includePatterns; }
        public RequestConfig setIncludePatterns(String[] includePatterns) { this.includePatterns = includePatterns; return this; }
        public List<String> getExcludePatterns() { return excludePatterns; }
        public SaHandler getHandler() { return handler; }
        public RequestConfig setHandler(SaHandler handler) { this.handler = handler; return this; }
    }
}