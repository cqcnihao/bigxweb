package com.cqcnihao.bigx.web.common.filter;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("<script.*?>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
            Pattern.compile("data:", Pattern.CASE_INSENSITIVE)
    };

    private static final Map<String, String> HTML_ESCAPE_MAP = new HashMap<>();

    static {
        HTML_ESCAPE_MAP.put("<", "&lt;");
        HTML_ESCAPE_MAP.put(">", "&gt;");
        HTML_ESCAPE_MAP.put("'", "&#39;");
        HTML_ESCAPE_MAP.put("\"", "&quot;");
        HTML_ESCAPE_MAP.put("&", "&amp;");
    }

    private static final Set<String> SKIP_HEADER_NAMES = Set.of(
            "Content-Type", "Accept", "Accept-Encoding", "Accept-Language",
            "Host", "User-Agent", "Connection", "Keep-Alive", "Cache-Control",
            "Authorization", "Cookie", "Set-Cookie", "Content-Length",
            "Content-Encoding", "Content-Language", "Content-Disposition",
            "Transfer-Encoding", "TE", "Trailers", "Upgrade",
            "X-Requested-With", "X-Forwarded-For", "X-Forwarded-Proto",
            "X-Gateway-Name", "X-Trace-Id"
    );

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return cleanXss(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleanValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleanValues[i] = cleanXss(values[i]);
        }
        return cleanValues;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        if (name != null && SKIP_HEADER_NAMES.contains(name)) {
            return value;
        }
        return cleanXss(value);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameterMap = super.getParameterMap();
        Map<String, String[]> cleanMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values != null) {
                String[] cleanValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    cleanValues[i] = cleanXss(values[i]);
                }
                cleanMap.put(entry.getKey(), cleanValues);
            }
        }
        return cleanMap;
    }

    private String cleanXss(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String cleanValue = value;
        for (Pattern pattern : XSS_PATTERNS) {
            cleanValue = pattern.matcher(cleanValue).replaceAll("");
        }
        for (Map.Entry<String, String> entry : HTML_ESCAPE_MAP.entrySet()) {
            cleanValue = cleanValue.replace(entry.getKey(), entry.getValue());
        }
        if (!value.equals(cleanValue)) {
            log.warn("XSS filtered: orig={}, clean={}",
                    value.length() > 50 ? value.substring(0, 50) + "..." : value,
                    cleanValue.length() > 50 ? cleanValue.substring(0, 50) + "..." : cleanValue);
        }
        return cleanValue;
    }

    public static boolean containsXss(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }
}