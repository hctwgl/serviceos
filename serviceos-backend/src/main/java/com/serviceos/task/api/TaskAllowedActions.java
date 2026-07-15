package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;

/** 当前主体在指定 Task 版本上可渲染的动作集合，不构成后续命令的授权凭证。 */
public record TaskAllowedActions(
        long resourceVersion,
        List<TaskAllowedAction> actions,
        Instant asOf
) {
    public TaskAllowedActions {
        if (resourceVersion < 1) {
            throw new IllegalArgumentException("resourceVersion must be positive");
        }
        actions = List.copyOf(actions);
    }
}
