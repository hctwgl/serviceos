package com.serviceos.workflow.api;

/**
 * 唤醒 WAIT_EVENT 订阅的命令。
 *
 * <p>{@code signalId} 是信号幂等键：同键重放返回首次结果；不得用它覆盖不同业务关联。</p>
 */
public record SignalWorkflowWaitCommand(
        String tenantId,
        String waitEventType,
        String correlationKey,
        String signalId,
        String correlationId
) {
    public SignalWorkflowWaitCommand {
        tenantId = required(tenantId, "tenantId");
        waitEventType = required(waitEventType, "waitEventType");
        correlationKey = required(correlationKey, "correlationKey");
        signalId = required(signalId, "signalId");
        correlationId = required(correlationId, "correlationId");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
