package com.serviceos.integration.byd.infrastructure;

import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * CPIM V7.3.1 SHA-256 签名校验。
 *
 * <p>签名原文由 APP_KEY、Nonce、Cur_Time、业务参数和 AppSecret 按 key ASCII 升序拼接。
 * 当前只接受标量参数；嵌套对象必须先由具体协议适配器转换为车企规定的字符串。</p>
 */
public final class BydCpimSignatureVerifier {
    private final String appKey;
    private final String appSecret;
    private final Clock clock;
    private final Duration allowedSkew;

    public BydCpimSignatureVerifier(String appKey, String appSecret, Clock clock, Duration allowedSkew) {
        this.appKey = requireText(appKey, "appKey");
        this.appSecret = requireText(appSecret, "appSecret");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.allowedSkew = Objects.requireNonNull(allowedSkew, "allowedSkew");
        if (allowedSkew.isNegative() || allowedSkew.isZero()) {
            throw new IllegalArgumentException("allowedSkew must be positive");
        }
    }

    public Verification verify(BydCpimSignatureHeaders headers, Map<String, ?> businessParameters) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(businessParameters, "businessParameters");

        if (!MessageDigest.isEqual(
                appKey.getBytes(StandardCharsets.UTF_8),
                headers.appKey().getBytes(StandardCharsets.UTF_8))) {
            return Verification.rejected(Reason.UNKNOWN_APP_KEY);
        }

        Duration skew = Duration.between(headers.currentTime(), clock.instant()).abs();
        if (skew.compareTo(allowedSkew) > 0) {
            return Verification.rejected(Reason.TIMESTAMP_OUT_OF_WINDOW);
        }

        String expected = sign(headers.appKey(), headers.nonce(), headers.currentTime().getEpochSecond(), businessParameters);
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                headers.signature().getBytes(StandardCharsets.US_ASCII));
        return matches ? Verification.accepted() : Verification.rejected(Reason.SIGNATURE_MISMATCH);
    }

    public String sign(String requestAppKey, String nonce, long epochSecond, Map<String, ?> businessParameters) {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("APP_KEY", requireText(requestAppKey, "requestAppKey"));
        values.put("Cur_Time", Long.toString(epochSecond));
        values.put("Nonce", requireText(nonce, "nonce"));
        businessParameters.forEach((key, value) -> {
            String normalizedKey = requireText(key, "parameter key");
            if (value != null) {
                if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
                    throw new IllegalArgumentException("nested CPIM signing parameter is not supported: " + normalizedKey);
                }
                values.put(normalizedKey, String.valueOf(value));
            }
        });
        values.put("AppSecret", appSecret);

        StringBuilder source = new StringBuilder();
        values.forEach((key, value) -> source.append(key).append('=').append(value).append('&'));
        source.setLength(source.length() - 1);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public enum Reason {
        NONE,
        UNKNOWN_APP_KEY,
        TIMESTAMP_OUT_OF_WINDOW,
        SIGNATURE_MISMATCH
    }

    public record Verification(boolean valid, Reason reason) {
        public static Verification accepted() {
            return new Verification(true, Reason.NONE);
        }

        public static Verification rejected(Reason reason) {
            return new Verification(false, reason);
        }
    }
}
