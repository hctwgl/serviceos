package com.serviceos.readmodel.api;

import java.util.Objects;

/**
 * Network 工作台当日运营节奏桶（业务日程摘要，非虚构流程节点）。
 *
 * <p>计数来自本网点 ACTIVE 责任与 soft-gate 后的真实预约/整改/SLA 事实。</p>
 */
public record NetworkPortalWorkbenchTimelineBucket(
        String bucketCode,
        String label,
        int count,
        String summary
) {
    public static final String UNASSIGNED = "UNASSIGNED";
    public static final String AM_APPOINTMENTS = "AM_APPOINTMENTS";
    public static final String PM_APPOINTMENTS = "PM_APPOINTMENTS";
    public static final String EVENING_APPOINTMENTS = "EVENING_APPOINTMENTS";
    public static final String OPEN_CORRECTIONS = "OPEN_CORRECTIONS";
    public static final String SLA_AT_RISK = "SLA_AT_RISK";

    public NetworkPortalWorkbenchTimelineBucket {
        Objects.requireNonNull(bucketCode, "bucketCode");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(summary, "summary");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
