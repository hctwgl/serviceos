package com.serviceos.integration.byd.web;

import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.application.BydCpimUpdateOrderService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** BYD CPIM 安装订单更新入站端点（协议签名认证，非 OIDC）。 */
@RestController
@RequestMapping("/api/v1/integrations/byd/cpim/v7.3.1/update-orders")
final class BydCpimUpdateOrderController {
    private final BydCpimUpdateOrderService service;

    BydCpimUpdateOrderController(BydCpimUpdateOrderService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BydCpimInboundOrderResponse> receive(
            @RequestHeader("APP_KEY") String appKey,
            @RequestHeader("Nonce") String nonce,
            @RequestHeader("Cur_Time") String currentTime,
            @RequestHeader("Sign") String signature,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody byte[] rawPayload
    ) {
        BydCpimInboundOrderResponse response;
        try {
            response = service.receive(
                    new BydCpimSignatureHeaders(appKey, nonce, LocalDate.parse(currentTime), signature),
                    rawPayload,
                    correlationId);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            response = BydCpimInboundOrderResponse.rejected("INVALID_HEADERS", exception.getMessage());
        }
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response);
    }
}
