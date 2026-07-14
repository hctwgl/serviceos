package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** Task 模块拥有的执行保护窗命令边界，供后续可靠改派 saga 调用。 */
public interface TaskExecutionGuardService {
    TaskExecutionGuardReceipt acquire(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            AcquireTaskExecutionGuardCommand command);

    TaskExecutionGuardReceipt release(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ReleaseTaskExecutionGuardCommand command);
}
