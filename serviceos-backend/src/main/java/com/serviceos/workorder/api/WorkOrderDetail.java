package com.serviceos.workorder.api;

import java.time.Instant;

public record WorkOrderDetail(WorkOrderView workOrder, Instant asOf) {
}
