package com.serviceos.integration.api;

import java.util.UUID;

/** 已确认车企提审成功后，登记回调中唯一可用的订单到 Case 关联。 */
public record RegisterExternalReviewRouteCommand(
        String externalOrderCode,
        UUID reviewCaseId,
        String externalSubmissionRef,
        String callbackBatchRef,
        String mappingVersionId
) {
}
