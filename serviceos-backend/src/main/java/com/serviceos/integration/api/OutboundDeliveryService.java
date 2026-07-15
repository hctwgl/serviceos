package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

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
}
