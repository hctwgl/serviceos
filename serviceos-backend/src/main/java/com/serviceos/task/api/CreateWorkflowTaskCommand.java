package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 由工作流运行时创建的首类任务。所有上下文版本都在创建时冻结，后续执行不得读取最新流程定义。
 */
public record CreateWorkflowTaskCommand(
        String tenantId,
        UUID projectId,
        UUID workOrderId,
        UUID workflowInstanceId,
        UUID stageInstanceId,
        UUID workflowNodeInstanceId,
        String workflowNodeId,
        UUID workflowDefinitionVersionId,
        String workflowDefinitionDigest,
        UUID configurationBundleId,
        String configurationBundleDigest,
        String stageCode,
        String taskType,
        WorkflowTaskKind taskKind,
        String formRef,
        String slaRef,
        String assigneePolicyRef,
        String dispatchPolicyRef,
        String payloadRef,
        String payloadDigest,
        int priority,
        Instant readyAt,
        int maxAttempts,
        String correlationId,
        String causationId
) {
}
