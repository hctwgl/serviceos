package com.serviceos.integration.byd.application;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** BYD 2.6 回调反腐映射；未知、缺失或宽松格式均失败关闭。 */
final class BydCpimReviewCallbackMapper {
    static final String MAPPING_VERSION = "byd-ocean-shandong-review-callback-v1";
    private static final Set<String> FIELDS = Set.of(
            "orderCode", "result", "remark", "examinePerson", "examineDate");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    BydCpimMappedReviewCallback map(Map<String, Object> source) {
        if (!FIELDS.containsAll(source.keySet())) {
            throw new IllegalArgumentException("review callback contains unknown fields");
        }
        String orderCodeText = required(source, "orderCode", 200);
        String result = required(source, "result", 10);
        String examinePerson = required(source, "examinePerson", 50);
        String examineDateText = required(source, "examineDate", 50);
        String remark = optional(source, "remark", 200);
        if (!"1".equals(result) && !"2".equals(result)) {
            throw new IllegalArgumentException("result must be 1 or 2");
        }
        if ("2".equals(result) && remark == null) {
            throw new IllegalArgumentException("remark is required when result is 2");
        }

        var orderCodes = Arrays.stream(orderCodeText.split(",", -1)).toList();
        if (orderCodes.isEmpty() || orderCodes.size() > 100) {
            throw new IllegalArgumentException("orderCode batch size must be between 1 and 100");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String orderCode : orderCodes) {
            if (orderCode.isBlank() || !orderCode.equals(orderCode.trim()) || orderCode.length() > 50) {
                throw new IllegalArgumentException("orderCode item is invalid");
            }
            if (!unique.add(orderCode)) {
                throw new IllegalArgumentException("orderCode batch contains a duplicate");
            }
        }
        try {
            return new BydCpimMappedReviewCallback(
                    unique.stream().toList(), result, "1".equals(result) ? "APPROVED" : "REJECTED",
                    remark, examinePerson, examineDateText, LocalDateTime.parse(examineDateText, DATE_TIME));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("examineDate must use yyyy-MM-dd HH:mm:ss", exception);
        }
    }

    private static String required(Map<String, Object> source, String field, int maximum) {
        String value = scalar(source, field, maximum);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String optional(Map<String, Object> source, String field, int maximum) {
        return scalar(source, field, maximum);
    }

    private static String scalar(Map<String, Object> source, String field, int maximum) {
        Object raw = source.get(field);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String value) || !value.equals(value.trim()) || value.length() > maximum) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
