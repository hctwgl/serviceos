package com.serviceos.fieldwork.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 离场必须引用本次 Visit 已完成的现场操作，防止空签退。 */
public record CheckOutVisitCommand(
        UUID visitId,
        long expectedVersion,
        Instant capturedAt,
        String resultCode,
        List<String> operationRefs
) {
    public CheckOutVisitCommand {
        visitId = Objects.requireNonNull(visitId, "visitId");
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        resultCode = reason(resultCode, "resultCode");
        operationRefs = references(operationRefs, "operationRefs", false);
    }

    static String reason(String value, String name) {
        value = CheckInVisitCommand.text(value, name, 100);
        if (!value.matches("[A-Z][A-Z0-9_]{1,99}")) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    static List<String> references(List<String> values, String name, boolean emptyAllowed) {
        values = values == null ? List.of() : List.copyOf(values);
        if ((!emptyAllowed && values.isEmpty()) || values.size() > 50
                || values.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 500)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return values;
    }
}
