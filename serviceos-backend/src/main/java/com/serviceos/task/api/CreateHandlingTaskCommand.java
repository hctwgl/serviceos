package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;

/**
 * 为人工异常闭环创建的 HUMAN Task；同一 tenant/taskType/businessKey 必须幂等。
 * candidatePrincipalIds 非空时同事务写入 SYSTEM CANDIDATE 快照。
 */
public record CreateHandlingTaskCommand(
        String tenantId,
        String taskType,
        String businessKey,
        String payloadRef,
        String payloadDigest,
        int priority,
        Instant readyAt,
        String correlationId,
        List<String> candidatePrincipalIds
) {
    public CreateHandlingTaskCommand(
            String tenantId,
            String taskType,
            String businessKey,
            String payloadRef,
            String payloadDigest,
            int priority,
            Instant readyAt,
            String correlationId
    ) {
        this(tenantId, taskType, businessKey, payloadRef, payloadDigest, priority, readyAt,
                correlationId, List.of());
    }

    public CreateHandlingTaskCommand {
        candidatePrincipalIds = candidatePrincipalIds == null
                ? List.of()
                : candidatePrincipalIds.stream()
                        .map(id -> id == null ? "" : id.trim())
                        .filter(id -> !id.isEmpty())
                        .distinct()
                        .toList();
    }
}
