package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

public interface WorkOrderTimelineQueryService {
    WorkOrderTimelinePage list(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            String cursor,
            int limit);
}
