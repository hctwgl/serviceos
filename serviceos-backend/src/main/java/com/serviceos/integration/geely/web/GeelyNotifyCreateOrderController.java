package com.serviceos.integration.geely.web;

import com.serviceos.integration.geely.api.GeelyNotifyResponse;
import com.serviceos.integration.geely.application.GeelyInboundCreateOrderService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 吉利浩瀚 7.1 {@code /notify_create_order} 本地入站端点。
 *
 * <p>路径保持协议语义；前置统一挂在 ServiceOS integrations 命名空间下。
 * 开放平台签名未接入（BLOCKED_EXTERNAL）。</p>
 */
@RestController
@RequestMapping("/api/v1/integrations/geely/haohan/v1.3/notify_create_order")
final class GeelyNotifyCreateOrderController {
    private final GeelyInboundCreateOrderService service;

    GeelyNotifyCreateOrderController(GeelyInboundCreateOrderService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<GeelyNotifyResponse> receive(
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody byte[] rawPayload
    ) {
        GeelyNotifyResponse response = service.receive(rawPayload, correlationId);
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response);
    }
}
