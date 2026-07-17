package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 轻量同步摘要：待处理 feed / 预约窗口 / tombstone 计数。不含离线命令运行时。
 */
public record TechnicianPortalSyncSummary(
        UUID networkId,
        int pendingFeedItemCount,
        int appointmentWindowCount,
        int tombstoneCount,
        Instant asOf
) {
}
