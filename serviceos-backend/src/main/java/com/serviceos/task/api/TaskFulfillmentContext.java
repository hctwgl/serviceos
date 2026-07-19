package com.serviceos.task.api;

import java.util.UUID;

/** 现场履约模块读取的最小 Task 权威上下文；不暴露 Task 内部持久化对象。 */
public record TaskFulfillmentContext(
        UUID taskId,
        UUID projectId,
        UUID workOrderId,
        UUID configurationBundleId,
        String configurationBundleDigest,
        String stageCode,
        String taskType,
        String taskKind,
        String formRef,
        String slaRef,
        String assigneePolicyRef,
        String dispatchPolicyRef,
        String ruleRef,
        String status,
        String responsiblePrincipalId,
        boolean executionGuarded,
        long version
) {
}
