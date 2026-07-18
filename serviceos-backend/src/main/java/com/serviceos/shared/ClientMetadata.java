package com.serviceos.shared;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 多端请求的低基数可观测元数据。该元数据只用于兼容诊断，绝不参与身份、授权或数据范围判断。
 */
public final class ClientMetadata {
    public static final String KIND_HEADER = "X-ServiceOS-Client-Kind";
    public static final String VERSION_HEADER = "X-ServiceOS-Client-Version";
    public static final String KIND_ATTRIBUTE = "com.serviceos.shared.ClientMetadata.kind";
    public static final String VERSION_ATTRIBUTE = "com.serviceos.shared.ClientMetadata.version";
    public static final String UNKNOWN_KIND = "UNKNOWN";
    public static final String UNSPECIFIED_VERSION = "UNSPECIFIED";

    private static final Set<String> KINDS = Set.of(
            "ADMIN_WEB", "NETWORK_WEB", "TECHNICIAN_WEB", "TECHNICIAN_IOS");
    private static final Pattern VERSION = Pattern.compile(
            "[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]{1,32})?");

    private ClientMetadata() {
    }

    /** 不可信或不完整的一对 Header 整体降为有界哨兵，原文绝不进入日志或 Trace。 */
    public static Normalized normalize(String suppliedKind, String suppliedVersion) {
        String kind = suppliedKind == null ? "" : suppliedKind.trim();
        String version = suppliedVersion == null ? "" : suppliedVersion.trim();
        if (!KINDS.contains(kind) || !VERSION.matcher(version).matches()) {
            return new Normalized(UNKNOWN_KIND, UNSPECIFIED_VERSION);
        }
        return new Normalized(kind, version);
    }

    public record Normalized(String kind, String version) {
    }
}
