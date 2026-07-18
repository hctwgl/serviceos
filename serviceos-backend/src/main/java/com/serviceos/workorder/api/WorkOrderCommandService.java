package com.serviceos.workorder.api;

public interface WorkOrderCommandService {
    WorkOrderReceipt receive(ReceiveExternalWorkOrderCommand command);

    WorkOrderActivationReceipt activate(ActivateWorkOrderCommand command);

    WorkOrderFulfillmentReceipt fulfill(FulfillWorkOrderCommand command);

    WorkOrderCancellationReceipt cancel(CancelWorkOrderCommand command);

    WorkOrderReopenReceipt reopen(ReopenWorkOrderCommand command);
}
