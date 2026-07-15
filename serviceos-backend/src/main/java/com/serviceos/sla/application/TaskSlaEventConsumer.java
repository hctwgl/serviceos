package com.serviceos.sla.application;

import com.serviceos.reliability.spi.OutboxMessage;

import java.time.Instant;
import java.util.UUID;

/** SLA 模块内部的 Task 事件消费端口；避免事务代理被按具体实现类注入。 */
interface TaskSlaEventConsumer {
    void start(OutboxMessage message, UUID taskId, String eventTaskType, Instant startedAt);

    void stop(OutboxMessage message, UUID taskId, Instant completedAt);
}
