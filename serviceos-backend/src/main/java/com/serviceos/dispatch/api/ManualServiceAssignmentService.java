package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/**
 * Admin/Portal 人工初派与同网点改派编排边界：确保容量并经 ServiceAssignment 激活 saga。
 * <p>
 * 能力：调用方须持有 {@code dispatch.assignment.manage} 与
 * {@code dispatch.capacity.configure}（试点确保容量上限时）。
 */
public interface ManualServiceAssignmentService {
    ManualServiceAssignmentReceipt manualAssign(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ManualAssignServiceAssignmentCommand command);

    /**
     * 仅激活 ACTIVE NETWORK（网点接单），不强制同时指派师傅。
     * 已有相同网点 ACTIVE NETWORK 时幂等回放；不同网点 ACTIVE 失败关闭。
     */
    NetworkPortalAcceptAssignmentReceipt manualAssignNetwork(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID taskId,
            String networkAssigneeId,
            String businessType);

    /**
     * 同网点师傅改派：要求已有 ACTIVE NETWORK（等于 networkAssigneeId）与不同 ACTIVE TECHNICIAN；
     * 复用 prepare(supersedes)+confirm+activate+complete。
     */
    ManualServiceAssignmentReceipt reassignTechnician(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ManualReassignTechnicianCommand command);
}
