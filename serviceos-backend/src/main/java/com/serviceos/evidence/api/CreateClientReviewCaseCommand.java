package com.serviceos.evidence.api;

import java.util.UUID;

/**
 * 登记总部已完成车企回传后创建 CLIENT 审核案例。
 *
 * @param sourceReviewCaseId 已通过的 INTERNAL 审核案例
 * @param externalSubmissionRef 车企侧本次提交的唯一引用
 * @param callbackBatchRef 后续回执必须匹配的批次引用
 * @param mappingVersionId 本次回传锁定的映射版本
 * @param policyVersion CLIENT 审核策略版本，必须由调用方显式提供
 */
public record CreateClientReviewCaseCommand(
        UUID sourceReviewCaseId,
        String externalSubmissionRef,
        String callbackBatchRef,
        String mappingVersionId,
        String policyVersion
) {
}
