package com.serviceos.operations.application;

import com.serviceos.operations.api.OperationalExceptionAcknowledgement;
import com.serviceos.operations.api.OperationalExceptionItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 应用层持久化端口；MyBatis Mapper 只允许由 infrastructure 适配器调用。 */
public interface OperationalExceptionWorkbenchRepository {
    List<OperationalExceptionItem> findPage(
            String tenantId, String status, String category, String severity,
            UUID workOrderId, UUID taskId, Instant cursorOpenedAt, UUID cursorId, int fetchSize);

    Optional<OperationalExceptionItem> findById(String tenantId, UUID exceptionId);

    boolean acknowledge(String tenantId, UUID exceptionId, long expectedVersion,
                        String actorId, String note, Instant acknowledgedAt);

    void saveAcknowledgement(String tenantId, String idempotencyKey,
                             OperationalExceptionAcknowledgement acknowledgement);

    OperationalExceptionAcknowledgement findAcknowledgement(String tenantId, String idempotencyKey);
}
