package com.serviceos.operations.api;

import java.util.UUID;

public record AcknowledgeOperationalExceptionCommand(UUID exceptionId, long expectedVersion, String note) {
    public AcknowledgeOperationalExceptionCommand {
        if (exceptionId == null || expectedVersion < 1) {
            throw new IllegalArgumentException("exceptionId and positive expectedVersion are required");
        }
        note = note == null ? null : note.trim();
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("note exceeds 500 characters");
        }
    }
}
