package com.serviceos.evidence.api;

import java.util.UUID;

/** Technician 在线提交任务只接受权威对象 ID；引用 URI 与摘要由服务端重读后组装。 */
public record TechnicianCompleteTaskCommand(
        UUID taskId,
        long expectedTaskVersion,
        UUID evidenceSetSnapshotId,
        UUID formSubmissionId
) {
    public TechnicianCompleteTaskCommand {
        if (taskId == null || evidenceSetSnapshotId == null) {
            throw new IllegalArgumentException("taskId and evidenceSetSnapshotId must not be null");
        }
        if (expectedTaskVersion <= 0) {
            throw new IllegalArgumentException("expectedTaskVersion must be positive");
        }
    }
}
