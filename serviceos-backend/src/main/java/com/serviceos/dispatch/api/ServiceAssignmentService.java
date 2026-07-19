package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** Dispatch 侧 ServiceAssignment 与容量激活 saga 命令边界。 */
public interface ServiceAssignmentService {
    /**
     * Inbox 可靠消费路径：冻结 DISPATCH 决议后激活 NETWORK 责任。
     *
     * <p>仅供 {@code task.created} 派单消费者调用；不暴露 HTTP。审计 actor 固定为
     * {@code system:dispatch-policy}；走 protocol v1 初始指派（prepare→confirm→activate→complete）。</p>
     */
    ServiceAssignmentReceipt activateNetworkFromFrozenDispatchPolicy(
            String tenantId,
            String correlationId,
            ActivateNetworkFromFrozenDispatchCommand command);

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
