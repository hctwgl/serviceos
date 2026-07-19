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

    /**
     * 对 UNKNOWN Delivery 发起远端状态查询（观察结果，不自动改写状态）。
     */
    RemoteStatusQueryView queryRemoteStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            QueryRemoteStatusCommand command);

    /**
     * 人工确认外部已处理或放弃 UNKNOWN Delivery；不创建 CLIENT Case/Route。
     */
    ManualDispositionView recordManualAck(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            RecordManualAckCommand command);

    OutboundDeliveryView get(CurrentPrincipal principal, String correlationId, UUID deliveryId);

    /**
     * 按工单列出外发 Delivery；同时要求 workOrder.read 与 integration.readOutbound 的
     * 实时 Project Scope。
     */
    List<OutboundDeliveryView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit);
}
