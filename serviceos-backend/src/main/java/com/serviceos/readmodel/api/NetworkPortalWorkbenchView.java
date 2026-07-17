package com.serviceos.readmodel.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Network Portal 工作台计数/摘要。
 *
 * <p>可选 enrichment 计数字段在无对应能力时为 {@code null}，经 {@link JsonInclude.Include#NON_NULL}
 * 从 JSON 省略；有能力且计数为 0 时仍序列化为 0。</p>
 *
 * <p>M224：可选 {@code slaSummary} 在无 NETWORK {@code sla.read} 时为 null（省略）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkPortalWorkbenchView(
        UUID networkId,
        int activeWorkOrderCount,
        int activeTaskCount,
        int activeTechnicianCount,
        List<NetworkPortalCapacityItem> capacity,
        Instant asOf,
        Integer unassignedTechnicianTaskCount,
        Integer openCorrectionCaseCount,
        Integer openOperationalExceptionCount,
        Integer pendingQualificationCount,
        NetworkPortalWorkOrderWorkspaceSlaSummary slaSummary
) {
    public NetworkPortalWorkbenchView {
        capacity = capacity == null ? List.of() : List.copyOf(capacity);
    }
}
