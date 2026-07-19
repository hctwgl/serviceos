package com.serviceos.configuration.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 生产师傅端静态能力目录。
 *
 * <p>反映当前已验收的 H5/iOS 在线履约能力，不得用目录伪造尚未交付的运行时。
 * 条件类能力：H5（M349/M350）已支持；iOS 尚无共用执行器，故仅 WEB 登记。</p>
 */
final class ClientCapabilityCatalog {
    static final String TECHNICIAN_WEB = "TECHNICIAN_WEB";
    static final String TECHNICIAN_IOS = "TECHNICIAN_IOS";

    private static final Set<String> SCALAR_FIELD_TYPES = Set.of(
            "STRING", "TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME");
    private static final Set<String> BASIC_MEDIA_TYPES = Set.of("PHOTO", "VIDEO", "DOCUMENT");

    private static final Map<String, Set<String>> PROFILES = new LinkedHashMap<>();

    static {
        PROFILES.put(TECHNICIAN_WEB, buildWeb());
        PROFILES.put(TECHNICIAN_IOS, buildIos());
    }

    private ClientCapabilityCatalog() {
    }

    static Set<String> productionTechnicianKinds() {
        return PROFILES.keySet();
    }

    static Set<String> capabilitiesOf(String clientKind) {
        return PROFILES.getOrDefault(clientKind, Set.of());
    }

    private static Set<String> buildWeb() {
        var caps = new java.util.LinkedHashSet<String>();
        for (String type : SCALAR_FIELD_TYPES) {
            caps.add(ClientCapabilityCodes.formFieldType(type));
        }
        for (String media : BASIC_MEDIA_TYPES) {
            caps.add(ClientCapabilityCodes.evidenceMediaType(media));
        }
        caps.add(ClientCapabilityCodes.FORM_VISIBLE_WHEN);
        caps.add(ClientCapabilityCodes.FORM_REQUIRED_WHEN);
        caps.add(ClientCapabilityCodes.FORM_SECTION_VISIBILITY);
        caps.add(ClientCapabilityCodes.FORM_VALIDATION_RULES);
        caps.add(ClientCapabilityCodes.EVIDENCE_REQUIRED_WHEN);
        return Set.copyOf(caps);
    }

    private static Set<String> buildIos() {
        var caps = new java.util.LinkedHashSet<String>();
        for (String type : SCALAR_FIELD_TYPES) {
            caps.add(ClientCapabilityCodes.formFieldType(type));
        }
        for (String media : BASIC_MEDIA_TYPES) {
            caps.add(ClientCapabilityCodes.evidenceMediaType(media));
        }
        // iOS 尚无与 H5 共用的条件/规则执行器；不得登记为已支持。
        return Set.copyOf(caps);
    }
}
