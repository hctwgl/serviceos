package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** Dispatch 侧 ServiceAssignment 与容量激活 saga 命令边界。 */
public interface ServiceAssignmentService {
    ServiceAssignmentReceipt prepare(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            PrepareServiceAssignmentCommand command);

    ServiceAssignmentReceipt confirmTaskPrepared(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ConfirmTaskAssignmentPreparedCommand command);

    ServiceAssignmentReceipt activate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ActivateServiceAssignmentCommand command);

    ServiceAssignmentReceipt abort(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AbortServiceAssignmentActivationCommand command);

    ServiceAssignmentReceipt completeAbort(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CompleteServiceAssignmentAbortCommand command);

    ServiceAssignmentReceipt complete(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CompleteServiceAssignmentActivationCommand command);
}
