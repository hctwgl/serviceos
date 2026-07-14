package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** TaskAssignment 命令边界；Task 是责任事实的聚合根。 */
public interface TaskAssignmentService {
    TaskAssignmentBatchReceipt assignCandidates(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AssignTaskCandidatesCommand command);
}
