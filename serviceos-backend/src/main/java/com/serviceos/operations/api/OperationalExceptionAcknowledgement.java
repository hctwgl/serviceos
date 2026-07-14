package com.serviceos.operations.api;

import java.time.Instant;
import java.util.UUID;

public record OperationalExceptionAcknowledgement(
        UUID exceptionId, String status, long aggregateVersion,
        Instant acknowledgedAt, String acknowledgedBy
) {}
