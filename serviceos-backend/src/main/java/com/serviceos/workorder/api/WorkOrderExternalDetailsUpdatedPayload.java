package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderExternalDetailsUpdatedPayload(
        UUID workOrderId,
        String updateDigest,
        String provinceCode,
        String cityCode,
        String districtCode,
        Instant updatedAt
) {
}
