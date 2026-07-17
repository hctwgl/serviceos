package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/**
 * Admin 人工初派编排边界：确保容量并经 ServiceAssignment 激活 saga 落到 ACTIVE。
 * <p>
 * 能力：调用方须持有 {@code dispatch.assignment.manage} 与
 * {@code dispatch.capacity.configure}（试点确保容量上限时）。
 */
public interface ManualServiceAssignmentService {
    ManualServiceAssignmentReceipt manualAssign(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ManualAssignServiceAssignmentCommand command);
}
