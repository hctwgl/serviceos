package com.serviceos.integration.referenceoem.web;

import com.serviceos.integration.referenceoem.api.ReferenceOemInboundOrderResponse;
import com.serviceos.integration.referenceoem.application.ReferenceOemInboundUpdateOrderService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REFERENCE / SAMPLE 更新入站端点（TBD_EXTERNAL_CONTRACT）。
 */
@RestController
@RequestMapping("/api/v1/integrations/reference-oem/sample/v1/update-orders")
final class ReferenceOemInboundUpdateOrderController {
    private final ReferenceOemInboundUpdateOrderService service;

    ReferenceOemInboundUpdateOrderController(ReferenceOemInboundUpdateOrderService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ReferenceOemInboundOrderResponse> receive(
            @RequestHeader("X-Reference-Oem-Key") String appKey,
            @RequestHeader("X-Reference-Oem-Nonce") String nonce,
            @RequestHeader("X-Reference-Oem-Signature") String signature,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody byte[] rawPayload
    ) {
        ReferenceOemInboundOrderResponse response;
        try {
            response = service.receive(appKey, nonce, signature, rawPayload, correlationId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            response = ReferenceOemInboundOrderResponse.rejected(
                    "REPLAY_CONFLICT", exception.getMessage());
        }
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response);
    }
}
