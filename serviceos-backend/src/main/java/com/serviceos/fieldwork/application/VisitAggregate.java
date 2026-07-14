package com.serviceos.fieldwork.application;

import com.serviceos.fieldwork.api.VisitLocation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Visit 持久化聚合；签到身份和定位为不可变首事实，终态字段只允许追加一次。 */
public record VisitAggregate(
        UUID visitId, String tenantId, UUID projectId, UUID workOrderId, UUID taskId,
        UUID appointmentId, int visitSequence, String technicianId, String networkId,
        String status, Instant checkInCapturedAt, Instant checkInReceivedAt,
        VisitLocation checkInLocation, String geofenceResult, Double geofenceDistanceMeters,
        String geofencePolicyVersion, String policyDecision, String deviceId,
        String deviceCommandId, boolean offline, Instant checkOutCapturedAt,
        Instant checkOutReceivedAt, String resultCode, String exceptionCode, String note,
        List<String> operationRefs, List<String> evidenceRefs, long aggregateVersion,
        String createdBy, Instant createdAt, Instant updatedAt
) {
}
