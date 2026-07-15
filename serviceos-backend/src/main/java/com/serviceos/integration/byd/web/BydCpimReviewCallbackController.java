package com.serviceos.integration.byd.web;

import com.serviceos.integration.byd.api.BydCpimReviewCallbackResponse;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.application.BydCpimReviewCallbackService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** BYD V7.3.1 2.6 厂端审核结果回调；身份只来自 CPIM 签名。 */
@RestController
@RequestMapping("/api/v1/integrations/byd/cpim/v7.3.1/review-results")
final class BydCpimReviewCallbackController {
    private final BydCpimReviewCallbackService service;

    BydCpimReviewCallbackController(BydCpimReviewCallbackService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BydCpimReviewCallbackResponse> receive(
            @RequestHeader("APP_KEY") String appKey,
            @RequestHeader("Nonce") String nonce,
            @RequestHeader("Cur_Time") String currentDate,
            @RequestHeader("Sign") String signature,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody byte[] rawPayload
    ) {
        BydCpimReviewCallbackResponse response;
        try {
            response = service.receive(
                    new BydCpimSignatureHeaders(appKey, nonce, LocalDate.parse(currentDate), signature),
                    rawPayload, correlationId);
        } catch (IllegalArgumentException exception) {
            response = BydCpimReviewCallbackResponse.rejected("INVALID_HEADERS");
        }
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(response);
    }
}
