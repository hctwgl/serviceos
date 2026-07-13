package com.serviceos.integration.byd.web;

import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.application.BydCpimInboundOrderService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * BYD CPIM V7.3.1 安装订单入站端点。
 *
 * <p>该端点不使用 OIDC JWT，而使用 CPIM APP_KEY/Nonce/Cur_Time/Sign 协议认证。</p>
 */
@RestController
@RequestMapping("/api/v1/integrations/byd/cpim/v7.3.1/install-orders")
final class BydCpimInboundOrderController {
    private final BydCpimInboundOrderService service;

    BydCpimInboundOrderController(BydCpimInboundOrderService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BydCpimInboundOrderResponse> receive(
            @RequestHeader("APP_KEY") String appKey,
            @RequestHeader("Nonce") String nonce,
            @RequestHeader("Cur_Time") long currentTime,
            @RequestHeader("Sign") String signature,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody Map<String, Object> rawParameters) {
        BydCpimInboundOrderResponse response;
        try {
            response = service.receive(
                    new BydCpimSignatureHeaders(appKey, nonce, Instant.ofEpochSecond(currentTime), signature),
                    rawParameters);
        } catch (IllegalArgumentException exception) {
            response = BydCpimInboundOrderResponse.rejected("INVALID_HEADERS", exception.getMessage());
        }
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response);
    }
}
