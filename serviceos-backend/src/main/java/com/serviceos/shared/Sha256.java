package com.serviceos.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 跨模块稳定摘要算法。摘要用于一致性和审计关联，不替代密码哈希或数字签名。
 */
public final class Sha256 {
    private Sha256() {
    }

    public static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String digest(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value);
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
