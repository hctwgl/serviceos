package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Technician Portal 当前责任任务的非 PII 在线详情。
 *
 * <p>该 DTO 只暴露师傅完成下一步导航所需的任务头、执行保护状态、预约和联系安全摘要；不包含地址、
 * 联系人、表单值、资料文件、配置源码或其他网点信息。写动作仍由后续领域命令及实时授权决定。</p>
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
