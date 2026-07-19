package com.serviceos.workorder.api;

public interface WorkOrderCommandService {
    WorkOrderReceipt receive(ReceiveExternalWorkOrderCommand command);

    WorkOrderActivationReceipt activate(ActivateWorkOrderCommand command);

    WorkOrderFulfillmentReceipt fulfill(FulfillWorkOrderCommand command);

    WorkOrderCancellationReceipt cancel(CancelWorkOrderCommand command);

    WorkOrderReopenReceipt reopen(ReopenWorkOrderCommand command);

    /** 更新外部联系/地址事实；不改变 Bundle 锁定。 */
    WorkOrderUpdateReceipt updateExternalDetails(UpdateExternalWorkOrderCommand command);
}
