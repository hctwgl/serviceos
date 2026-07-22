package com.serviceos.readmodel.api;

import java.time.Instant;

/**
 * Admin 首页的页面级任务投影。
 *
 * <p>所有数量均在当前主体实时授权范围内计算；工作台不保存第二套任务状态，也不执行领域命令。</p>
 */
public record AdminWorkbenchView(
        int priorityCount,
        int reviewCount,
        int correctionCount,
        int dispatchCount,
        int slaRiskCount,
        int exceptionCount,
        int waitingExternalCount,
        int unassignedCount,
        Instant generatedAt
) {
}
