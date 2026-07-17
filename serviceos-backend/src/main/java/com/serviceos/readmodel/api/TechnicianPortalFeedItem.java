package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Technician Portal task-feed 项。TOMBSTONE 仅含 taskId 与 invalidationReason。
 */
public record TechnicianPortalFeedItem(
        String itemType,
        UUID taskId,
        UUID workOrderId,
        UUID projectId,
        UUID serviceAssignmentId,
        UUID taskAssignmentId,
        String taskType,
        String taskKind,
        String stageCode,
        String taskStatus,
        String businessType,
        Instant effectiveFrom,
        String cursor,
        String invalidationReason
) {
}
