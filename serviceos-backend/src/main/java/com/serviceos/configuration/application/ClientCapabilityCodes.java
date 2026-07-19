package com.serviceos.configuration.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** 客户端能力稳定英文编码与中文说明。 */
final class ClientCapabilityCodes {
    static final String FORM_FIELD_PREFIX = "form.fieldType.";
    static final String EVIDENCE_MEDIA_PREFIX = "evidence.mediaType.";
    static final String FORM_VISIBLE_WHEN = "form.condition.visibleWhen";
    static final String FORM_REQUIRED_WHEN = "form.condition.requiredWhen";
    static final String FORM_SECTION_VISIBILITY = "form.condition.sectionVisibility";
    static final String FORM_VALIDATION_RULES = "form.rule.validationRules";
    static final String FORM_OPTIONS_REF = "form.field.optionsRef";
    static final String EVIDENCE_REQUIRED_WHEN = "evidence.condition.requiredWhen";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        for (String type : new String[] {
                "STRING", "TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME",
                "ENUM", "MULTI_ENUM", "ADDRESS", "GEOPOINT", "SIGNATURE", "FILE_REF",
                "OBJECT", "OBJECT_LIST"
        }) {
            LABELS.put(FORM_FIELD_PREFIX + type, "表单字段类型 " + type);
        }
        for (String media : new String[] {
                "PHOTO", "VIDEO", "DOCUMENT", "SIGNATURE", "GENERATED_REPORT"
        }) {
            LABELS.put(EVIDENCE_MEDIA_PREFIX + media, "资料媒体类型 " + media);
        }
        LABELS.put(FORM_VISIBLE_WHEN, "表单字段条件显隐 visibleWhen");
        LABELS.put(FORM_REQUIRED_WHEN, "表单字段条件必填 requiredWhen");
        LABELS.put(FORM_SECTION_VISIBILITY, "表单分区条件显隐");
        LABELS.put(FORM_VALIDATION_RULES, "表单跨字段校验规则");
        LABELS.put(FORM_OPTIONS_REF, "表单远程/引用选项 optionsRef");
        LABELS.put(EVIDENCE_REQUIRED_WHEN, "资料槽位条件必填 requiredWhen");
    }

    private ClientCapabilityCodes() {
    }

    static String formFieldType(String dataType) {
        return FORM_FIELD_PREFIX + dataType;
    }

    static String evidenceMediaType(String mediaType) {
        return EVIDENCE_MEDIA_PREFIX + mediaType;
    }

    static String label(String code) {
        return LABELS.getOrDefault(code, code);
    }
}
