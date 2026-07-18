package com.serviceos.workflow.api;

import java.util.UUID;

/** TIMER 到期唤醒端口。 */
public interface WorkflowTimerFireService {
    void fireTimer(UUID timerSubscriptionId, String correlationId);
}
