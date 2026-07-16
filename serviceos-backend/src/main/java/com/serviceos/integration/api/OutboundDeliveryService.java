package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** 外部交付命令与授权摘要查询边界。 */
public interface OutboundDeliveryService {
    OutboundDeliveryView createReviewSubmission(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateReviewSubmissionCommand command);

    DeliveryReplayRequestView retryUnknown(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            RetryOutboundDeliveryCommand command);

    OutboundDeliveryView get(CurrentPrincipal principal, String correlationId, UUID deliveryId);

    /**
     * 按工单列出外发 Delivery；同时要求 workOrder.read 与 integration.readOutbound 的
     * 实时 Project Scope。
     */
    List<OutboundDeliveryView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit);
}
