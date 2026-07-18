package com.serviceos.workflow.api;

/** WAIT_EVENT 幂等唤醒端口。 */
public interface WorkflowWaitSignalService {
    WorkflowWaitSignalResult signal(SignalWorkflowWaitCommand command);
}
