package com.serviceos.task.application;

import com.serviceos.reliability.spi.OutboxMessage;

import java.time.Instant;
import java.util.UUID;

/** Task 模块内部 ASSIGNEE_POLICY 消费端口。 */
interface TaskAssigneePolicyEventConsumer {
    void applyFrozenPolicy(OutboxMessage message, UUID taskId, Instant createdAt);
}
