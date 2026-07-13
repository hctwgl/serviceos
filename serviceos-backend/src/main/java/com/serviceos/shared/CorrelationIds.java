package com.serviceos.shared;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HTTP 与异步链路共用的关联标识约束。
 *
 * <p>外部调用方可以提供关联标识，但该值会进入响应头、日志 MDC 与 Trace 属性，
 * 因此必须先限制字符集和长度，避免响应头注入、日志伪造或把敏感正文伪装成关联标识。</p>
 */
public final class CorrelationIds {
    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String REQUEST_ATTRIBUTE = "com.serviceos.shared.CorrelationIds.current";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");

    private CorrelationIds() {
    }

    /**
     * 接受安全的调用方标识；缺失或不安全时生成服务端 UUID，绝不原样回显不可信输入。
     */
    public static String normalizeOrCreate(String supplied) {
        if (supplied != null) {
            String candidate = supplied.trim();
            if (SAFE_VALUE.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 从已经通过入口过滤器固化的请求属性读取关联标识。
     */
    public static String fromRequestAttribute(Object value) {
        if (value instanceof String correlationId && SAFE_VALUE.matcher(correlationId).matches()) {
            return correlationId;
        }
        // 理论上只会在未经过 Servlet 入口过滤器的内部测试中发生；仍返回安全值，避免空关联。
        return UUID.randomUUID().toString();
    }
}
