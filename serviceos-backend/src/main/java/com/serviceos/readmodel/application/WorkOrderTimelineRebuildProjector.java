package com.serviceos.readmodel.application;

import com.serviceos.reliability.spi.OutboxMessage;

/** 重建作业写入指定 generation 的投影端口（绕过 Inbox）。 */
interface WorkOrderTimelineRebuildProjector {
    void projectForRebuild(OutboxMessage message, int rebuildGeneration);
}
