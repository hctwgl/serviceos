package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M222：Network Portal 工作区 Visit 摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceVisitSummary}，故意不含 GPS/note/device。
 */
public record NetworkPortalWorkspaceVisitSummary(
        UUID visitId,
        UUID taskId,
        UUID appointmentId,
        int visitSequence,
        String technicianId,
        String networkId,
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
