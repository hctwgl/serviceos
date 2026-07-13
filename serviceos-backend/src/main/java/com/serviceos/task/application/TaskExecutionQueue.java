package com.serviceos.task.application;

import java.time.Duration;
import java.util.Optional;

/**
 * task 模块内部队列端口；claim 和 resolve 各自在独立短事务中执行。
 */
public interface TaskExecutionQueue {
    boolean recoverOneExhaustedLease(String recoveryWorkerId);

    Optional<ClaimedTask> claimNext(String workerId, Duration leaseDuration);

    TaskResolution resolve(ClaimedTask task, String workerId, TaskExecutionOutcome outcome);
}
