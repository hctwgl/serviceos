package com.serviceos.fieldwork.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Visit 当前投影；到场事实来自不可变首事件，终态事实来自后续追加事件。 */
public record VisitView(
        UUID visitId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        UUID appointmentId,
        int visitSequence,
        String technicianId,
        String networkId,
        String status,
        Instant checkInCapturedAt,
        Instant checkInReceivedAt,
        VisitLocation checkInLocation,
        String geofenceResult,
        Double geofenceDistanceMeters,
        String geofencePolicyVersion,
        String policyDecision,
        String deviceId,
        String deviceCommandId,
        boolean offline,
        Instant checkOutCapturedAt,
        Instant checkOutReceivedAt,
        String resultCode,
        String exceptionCode,
        String note,
        List<String> operationRefs,
        List<String> evidenceRefs,
        long aggregateVersion,
        List<String> allowedActions
) {
}
