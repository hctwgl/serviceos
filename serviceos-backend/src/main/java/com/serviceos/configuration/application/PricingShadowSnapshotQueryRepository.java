package com.serviceos.configuration.application;

import com.serviceos.configuration.api.PricingShadowSnapshotView;

import java.util.List;
import java.util.UUID;

/** cfg_calculation_snapshot 只读查询端口。 */
public interface PricingShadowSnapshotQueryRepository {
    List<PricingShadowSnapshotView> listByWorkOrder(String tenantId, UUID workOrderId);
}
