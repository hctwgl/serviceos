package com.serviceos.workorder.api;

public interface WorkOrderCommandService {
    WorkOrderReceipt receive(ReceiveExternalWorkOrderCommand command);

    WorkOrderActivationReceipt activate(ActivateWorkOrderCommand command);
}
