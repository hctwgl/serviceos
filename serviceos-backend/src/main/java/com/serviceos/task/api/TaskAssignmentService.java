package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** TaskAssignment 命令边界；Task 是责任事实的聚合根。 */
public interface TaskAssignmentService {
    TaskAssignmentBatchReceipt assignCandidates(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AssignTaskCandidatesCommand command);

    /**
     * Inbox 可靠消费路径：冻结 ASSIGNEE_POLICY 解析后写入候选快照。
     *
     * <p>仅供 {@code task.created} 消费者调用；不暴露 HTTP。仍强制 READY HUMAN、
     * 幂等与 ASSIGNEE_POLICY sourceType；审计 actor 固定为 {@code system:assignee-policy}。</p>
     */
    TaskAssignmentBatchReceipt assignCandidatesFromFrozenPolicy(
            String tenantId,
            String correlationId,
            AssignTaskCandidatesCommand command);
}
