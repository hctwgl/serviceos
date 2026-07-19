package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** 批量 UNKNOWN Delivery 重放预演/审批边界。 */
public interface BatchReplayService {
    BatchReplayRequestView create(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateBatchReplayCommand command);

    BatchReplayRequestView approve(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ApproveBatchReplayCommand command);

    BatchReplayRequestView get(
            CurrentPrincipal principal,
            String correlationId,
            java.util.UUID batchId);
}
