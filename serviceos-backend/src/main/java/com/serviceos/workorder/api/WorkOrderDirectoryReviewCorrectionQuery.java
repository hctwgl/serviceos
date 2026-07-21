package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 工单目录审核/整改状态筛选端口。
 *
 * <p>由 evidence 模块实现。调用方已完成 PROJECT {@code evidence.read} soft-gate 与项目范围收敛；
 * 本端口不再鉴权。</p>
 *
 * <p>筛选口径（运营桶）：
 * {@code REVIEW_OPEN}=任一任务存在 OPEN ReviewCase；
 * {@code CORRECTION_ACTIVE}=任一任务存在 OPEN|IN_PROGRESS|RESUBMITTED CorrectionCase。
 * 经 {@code task_id → work_order_id} 关联；EXISTS 任意匹配案。</p>
 */
public interface WorkOrderDirectoryReviewCorrectionQuery {

    /**
     * 解析具备指定审核/整改运营桶口径的工单 ID。
     *
     * @param reviewCorrectionStatus {@code REVIEW_OPEN} 或 {@code CORRECTION_ACTIVE}
     * @return 匹配工单 ID；无匹配时为空列表（调用方应失败关闭为无结果）
     */
    List<UUID> findWorkOrderIdsByReviewCorrectionStatus(
            String tenantId,
            String reviewCorrectionStatus,
            boolean tenantWide,
            Collection<UUID> projectIds
    );
}
