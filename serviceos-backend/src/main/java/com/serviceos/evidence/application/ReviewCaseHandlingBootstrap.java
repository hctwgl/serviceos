package com.serviceos.evidence.application;

import com.serviceos.evidence.api.ReviewCaseView;

import java.util.UUID;

/**
 * 同模块内创建带独立审核 Task 的 INTERNAL ReviewCase。
 *
 * <p>供 CorrectionCase.close 在同事务打开复审 Case，避免 Correction↔Review 服务循环依赖。</p>
 */
interface ReviewCaseHandlingBootstrap {
    /**
     * 为已存在的 TASK_SUBMISSION Snapshot 打开 OPEN INTERNAL ReviewCase，并绑定 reviewTaskId。
     *
     * @throws com.serviceos.shared.BusinessProblem 若 Snapshot 不存在、非 TASK_SUBMISSION、
     *         或该 Snapshot 已有活跃 INTERNAL Case
     */
    ReviewCaseView openInternalForSnapshot(
            String tenantId,
            String actorId,
            String correlationId,
            String causationId,
            UUID evidenceSetSnapshotId,
            String policyVersion
    );
}
