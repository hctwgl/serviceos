package com.serviceos.task.api;

import java.util.Objects;

/** 完成人工任务时冻结的结构化输入版本引用。 */
public record InputVersionRef(String kind, String ref, String digest) {
    public static final String FORM_SUBMISSION = "FORM_SUBMISSION";
    public static final String EVIDENCE_SET_SNAPSHOT = "EVIDENCE_SET_SNAPSHOT";

    public InputVersionRef {
        kind = required(kind, "kind", 40);
        ref = required(ref, "ref", 500);
        digest = required(digest, "digest", 64);
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("digest must be a SHA-256 hex digest");
        }
        if (!FORM_SUBMISSION.equals(kind) && !EVIDENCE_SET_SNAPSHOT.equals(kind)) {
            throw new IllegalArgumentException("unsupported inputVersionRef kind: " + kind);
        }
    }

    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds max length " + max);
        }
        return normalized;
    }
}
