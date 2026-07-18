package com.serviceos.workflow.api;

public interface WorkflowJumpService {
    WorkflowJumpReceipt jump(JumpWorkflowCommand command);
}
