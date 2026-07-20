package com.serviceos.workflow.api;

/**
 * M365 / A5-B：REVIEW_TASK 编排门闸的固定 WAIT 关联约定。
 *
 * <p>门闸不创建 HUMAN 工作流 Task；由 {@code evidence.review-decided}
 * （APPROVED / FORCE_APPROVED）以本事件类型唤醒，与 A2-R 的 handling {@code reviewTaskId}
 * 保持双轨职责分离。</p>
 */
public final class ReviewGateWait {
    public static final String WAIT_EVENT_TYPE = "serviceos.review.approved";
    public static final String CORRELATION_KEY_TEMPLATE = "workOrder:{workOrderId}";

    private ReviewGateWait() {
    }
}
