package com.serviceos.integration.geely.web;

import com.serviceos.integration.geely.api.GeelyNotifyResponse;
import com.serviceos.integration.geely.application.GeelyInboundReviewCallbackService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 吉利 7.13 核销审核结果通知本地入口。 */
@RestController
@RequestMapping("/api/v1/integrations/geely/haohan/v1.3/notify_settlement_audit_result")
final class GeelyNotifySettlementAuditController {
    private final GeelyInboundReviewCallbackService service;

    GeelyNotifySettlementAuditController(GeelyInboundReviewCallbackService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<GeelyNotifyResponse> receive(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody byte[] rawPayload
    ) {
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(service.receive(rawPayload, correlationId));
    }
}
