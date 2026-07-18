package com.serviceos.fieldwork.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Technician Visit 生命周期安全摘要；不含坐标、距离、设备、离线命令、note 或资料引用。
 */
public record TechnicianVisitHistoryView(
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
