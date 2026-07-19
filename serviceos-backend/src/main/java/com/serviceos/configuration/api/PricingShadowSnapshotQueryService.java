package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** 按工单读取 SHADOW 定价试算快照（只读，非结算）。 */
public interface PricingShadowSnapshotQueryService {
    List<PricingShadowSnapshotView> listByWorkOrder(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    );
}
