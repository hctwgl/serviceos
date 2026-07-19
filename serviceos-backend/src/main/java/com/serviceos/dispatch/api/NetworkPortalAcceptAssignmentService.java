package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/**
 * Network Portal 网点接单编排边界。
 * <p>
 * 能力：NETWORK scope {@code networkPortal.acceptAssignment}；
 * 强制 networkAssigneeId 来自可信 X-Network-Context；不接受客户端上报网点。
 */
public interface NetworkPortalAcceptAssignmentService {
    NetworkPortalAcceptAssignmentReceipt acceptAssignment(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            String businessType);
}
