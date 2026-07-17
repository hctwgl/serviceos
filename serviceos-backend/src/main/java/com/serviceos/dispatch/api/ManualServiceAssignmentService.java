package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

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
     * 同网点师傅改派：要求已有 ACTIVE NETWORK（等于 networkAssigneeId）与不同 ACTIVE TECHNICIAN；
     * 复用 prepare(supersedes)+confirm+activate+complete。
     */
    ManualServiceAssignmentReceipt reassignTechnician(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ManualReassignTechnicianCommand command);
}
