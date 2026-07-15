package com.serviceos.task.api;

import java.time.Instant;
import java.util.UUID;

public record TaskDirectoryItem(UUID id,UUID projectId,UUID workOrderId,String taskType,String taskKind,
        String stageCode,int priority,String status,Instant nextRunAt,String claimedBy,int attemptCount,
        int maxAttempts,long version,Instant createdAt,Instant updatedAt) {}
