package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Technician Portal 当前责任任务的非 PII 在线详情。
 *
 * <p>M350：额外暴露 SERVICEOS_EXPR_V1 白名单所需的工单/区域非 PII 头，供 H5 条件与
 * validationRules 与服务端共用同一权威上下文；仍不包含地址正文、联系人、表单值或资料文件。</p>
 */
public record TechnicianPortalTaskDetail(
        UUID networkId,
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
        boolean executionGuarded,
        long resourceVersion,
        String clientCode,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        List<TechnicianPortalScheduleItem> appointments,
        List<TechnicianPortalContactAttemptItem> contactAttempts,
        List<TechnicianPortalVisitItem> visits,
        List<TechnicianPortalFormSubmissionItem> formSubmissions,
        Instant asOf
) {
    public TechnicianPortalTaskDetail {
        if (resourceVersion < 1) {
            throw new IllegalArgumentException("resourceVersion must be positive");
        }
        appointments = List.copyOf(appointments);
        contactAttempts = List.copyOf(contactAttempts);
        visits = List.copyOf(visits);
        formSubmissions = formSubmissions == null ? null : List.copyOf(formSubmissions);
    }
}
