package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Technician 当前任务详情中的 Visit 生命周期安全摘要。 */
public record TechnicianPortalVisitItem(
        UUID visitId,
        UUID taskId,
        UUID appointmentId,
        int visitSequence,
        String status,
        Instant checkInCapturedAt,
        Instant checkInReceivedAt,
        String geofenceResult,
        String policyDecision,
        Instant checkOutCapturedAt,
        Instant checkOutReceivedAt,
        String resultCode,
        String exceptionCode,
        long aggregateVersion
) {
}
