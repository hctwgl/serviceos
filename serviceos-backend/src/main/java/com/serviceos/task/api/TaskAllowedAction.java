package com.serviceos.task.api;

import java.util.List;

/** Portal 可显式注册渲染的 Task 动作描述；code 与写命令 capability 保持一致。 */
public record TaskAllowedAction(
        String code,
        String label,
        String inputSchemaRef,
        List<String> obligations
) {
    public TaskAllowedAction {
        obligations = List.copyOf(obligations);
    }
}
