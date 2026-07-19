package com.serviceos.dispatch.application;

import com.serviceos.reliability.spi.OutboxMessage;

import java.time.Instant;
import java.util.UUID;

/** task.created → 冻结 DISPATCH → NETWORK ServiceAssignment。 */
interface TaskDispatchPolicyEventConsumer {
    void applyFrozenPolicy(OutboxMessage message, UUID taskId, Instant createdAt);
}
