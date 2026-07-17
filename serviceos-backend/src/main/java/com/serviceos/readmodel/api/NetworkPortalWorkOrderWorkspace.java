package com.serviceos.readmodel.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M213：Network Portal 限定工单工作区薄快照。
 * 仅含本网点 ACTIVE NETWORK 责任范围内的安全字段，不复用 Admin WorkOrderWorkspace。
 *
 * <p>M221：可选 {@code slaSummary} 在无 NETWORK {@code sla.read} 时为 null，
 * 经 {@link JsonInclude.Include#NON_NULL} 从 JSON 省略。</p>
 *
 * <p>M222：可选 {@code visits}/{@code formSubmissions} 在无 NETWORK
 * {@code visit.read}/{@code form.read} 时为 null（省略）；有能力时可为空列表。</p>
 *
 * <p>M223：可选 {@code evidenceSlots}/{@code evidenceItems} 在无 NETWORK
 * {@code evidence.read} 时为 null（同时省略）；有能力时可为空列表。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkPortalWorkOrderWorkspace(
        UUID networkId,
        UUID workOrderId,
        UUID projectId,
        List<UUID> taskIds,
        String businessType,
        String technicianId,
        Instant effectiveFrom,
        List<NetworkPortalTaskItem> tasks,
        NetworkPortalWorkOrderWorkspaceSlaSummary slaSummary,
        List<NetworkPortalWorkspaceVisitSummary> visits,
        List<NetworkPortalWorkspaceFormSubmissionSummary> formSubmissions,
        List<NetworkPortalWorkspaceEvidenceSlotSummary> evidenceSlots,
        List<NetworkPortalWorkspaceEvidenceItemSummary> evidenceItems,
        Instant asOf
) {
    public NetworkPortalWorkOrderWorkspace {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        visits = visits == null ? null : List.copyOf(visits);
        formSubmissions = formSubmissions == null ? null : List.copyOf(formSubmissions);
        evidenceSlots = evidenceSlots == null ? null : List.copyOf(evidenceSlots);
        evidenceItems = evidenceItems == null ? null : List.copyOf(evidenceItems);
    }
}
