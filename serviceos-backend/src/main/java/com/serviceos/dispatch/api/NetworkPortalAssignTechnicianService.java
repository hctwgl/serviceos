package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/**
 * Network Portal 指派师傅编排边界。
 * <p>
 * 能力：NETWORK scope {@code networkPortal.assignTechnician}；委托
 * {@link ManualServiceAssignmentService#manualAssign}，强制 networkAssigneeId 来自可信上下文。
 */
public interface NetworkPortalAssignTechnicianService {
    ManualServiceAssignmentReceipt assignTechnician(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            String technicianAssigneeId,
            String businessType);
}
