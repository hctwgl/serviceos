package com.serviceos.integration.byd.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * 为 BYD CPIM 入站请求生成稳定 SHA-256 摘要。
 *
 * <p>只接受标量值；嵌套对象必须先转换为协议定义的稳定字符串。</p>
 */
public final class BydCpimPayloadDigest {
    private BydCpimPayloadDigest() {
    }

    public static String sha256(Map<String, ?> parameters) {
        TreeMap<String, String> sorted = new TreeMap<>();
        parameters.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("parameter key must not be blank");
            }
            if (value != null) {
                if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
                    throw new IllegalArgumentException("nested parameter is not supported: " + key);
                }
                sorted.put(key.trim(), String.valueOf(value));
            }
        });

        StringBuilder canonical = new StringBuilder();
        sorted.forEach((key, value) -> canonical
                .append(key.length()).append(':').append(key)
                .append('=')
                .append(value.length()).append(':').append(value)
                .append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
