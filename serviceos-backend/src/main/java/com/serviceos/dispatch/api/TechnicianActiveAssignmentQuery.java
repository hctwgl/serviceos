package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 按师傅 assignee 与网点列出 TECHNICIAN ServiceAssignment。
 * 无 HTTP 暴露；供 Technician Portal 等已鉴权编排使用。
 */
public interface TechnicianActiveAssignmentQuery {
    /**
     * 当前 ACTIVE 责任（完整快照）。
     */
    List<TechnicianActiveAssignmentView> listActiveForTechnician(
            String tenantId, String networkId, Collection<String> assigneeIds);

    /**
     * 自 cursor 之后的变更：含新 ACTIVE 与 ENDED tombstone 候选。
     * {@code since} / {@code afterAssignmentId} 为 opaque cursor 解码结果。
     */
    List<TechnicianActiveAssignmentView> listChangesSince(
            String tenantId,
            String networkId,
            Collection<String> assigneeIds,
            Instant since,
            UUID afterAssignmentId);

    /**
     * 该师傅在网点范围内已 ENDED 的 TECHNICIAN 责任数量（轻量 sync-summary）。
     */
    int countEndedForTechnician(String tenantId, String networkId, Collection<String> assigneeIds);

    /**
     * 候选任务中属于该网点 NETWORK 责任（ACTIVE 或 ENDED）的子集，供 TaskAssignment 收敛。
     */
    List<UUID> filterTaskIdsForNetwork(
            String tenantId, String networkId, Collection<UUID> candidateTaskIds);
}
