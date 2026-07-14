package com.serviceos.fieldwork.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** Visit 聚合的公开应用边界。 */
public interface VisitService {
    List<VisitView> listByWorkOrder(CurrentPrincipal principal, String correlationId, UUID workOrderId);

    VisitCommandReceipt checkIn(
            CurrentPrincipal principal, CommandMetadata metadata, CheckInVisitCommand command);

    VisitCommandReceipt checkOut(
            CurrentPrincipal principal, CommandMetadata metadata, CheckOutVisitCommand command);

    VisitCommandReceipt interrupt(
            CurrentPrincipal principal, CommandMetadata metadata, InterruptVisitCommand command);
}
