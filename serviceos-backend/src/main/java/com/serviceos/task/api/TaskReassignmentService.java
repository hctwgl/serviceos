package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** Task 模块拥有的可靠责任切换边界。 */
public interface TaskReassignmentService {
    TaskReassignmentReceipt prepare(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            PrepareTaskReassignmentCommand command);

    TaskReassignmentReceipt activate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ActivatePreparedTaskAssignmentCommand command);

    TaskReassignmentReceipt abort(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AbortPreparedTaskAssignmentCommand command);
}
