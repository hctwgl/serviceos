package com.serviceos.task.api;

import java.util.List;

/** 当前不可执行的动作及用户可理解的阻塞原因。 */
public record TaskBlockedAction(
        String code,
        String label,
        List<String> blockingReasons
) {
    public TaskBlockedAction {
        blockingReasons = List.copyOf(blockingReasons == null ? List.of() : blockingReasons);
    }
}
