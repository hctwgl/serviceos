package com.serviceos.forms.api;

import java.util.Objects;
import java.util.UUID;

/** 向 Task 锁定的精确 FormVersion 提交一份不可变值文档。 */
public record SubmitFormCommand(UUID taskId, UUID formVersionId, String valuesJson, String prefillVersion) {
    public SubmitFormCommand {
        taskId = Objects.requireNonNull(taskId, "taskId");
        formVersionId = Objects.requireNonNull(formVersionId, "formVersionId");
        valuesJson = Objects.requireNonNull(valuesJson, "valuesJson").trim();
        if (valuesJson.isEmpty() || valuesJson.length() > 1_000_000) {
            throw new IllegalArgumentException("values must contain at most 1000000 characters");
        }
        if (prefillVersion != null) {
            prefillVersion = prefillVersion.trim();
            if (prefillVersion.isEmpty() || prefillVersion.length() > 160) {
                throw new IllegalArgumentException("prefillVersion is invalid");
            }
        }
    }
}
