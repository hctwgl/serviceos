package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskDetail(TaskDirectoryItem task,UUID workflowInstanceId,UUID stageInstanceId,
        UUID workflowNodeInstanceId,String workflowNodeId,UUID workflowDefinitionVersionId,
        String workflowDefinitionDigest,UUID configurationBundleId,String configurationBundleDigest,
        String formRef,String responsibleUserId,List<String> candidateUserIds,Instant claimedAt,
        Instant startedAt,Instant completedAt,String resultRef,String resultDigest,
        List<InputVersionRef> inputVersionRefs,Instant asOf) {
    public TaskDetail { candidateUserIds=List.copyOf(candidateUserIds);inputVersionRefs=List.copyOf(inputVersionRefs); }
}
