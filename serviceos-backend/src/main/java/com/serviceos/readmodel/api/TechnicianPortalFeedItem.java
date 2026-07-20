package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Technician Portal task-feed 项。TOMBSTONE 仅含 taskId 与 invalidationReason。
 *
 * <p>M359：ASSIGNMENT 项可带 {@code clientCapabilityUnsupportedDetail}，
 * 表示当前 clientKind 无法履约冻结 FORM/EVIDENCE；客户端应展示并阻止进入详情执行。</p>
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
        String invalidationReason,
        String clientCapabilityUnsupportedDetail
) {
    /** 兼容旧构造（无能力预检字段）。 */
    public TechnicianPortalFeedItem(
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
        this(itemType, taskId, workOrderId, projectId, serviceAssignmentId, taskAssignmentId,
                taskType, taskKind, stageCode, taskStatus, businessType, effectiveFrom, cursor,
                invalidationReason, null);
    }
}
