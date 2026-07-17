package com.serviceos.integration.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeQueuePage;
import com.serviceos.integration.api.InboundEnvelopeQueueQuery;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 管理查询只返回摘要；原始对象引用和传输签名不离开 integration 边界。 */
@RestController
@RequestMapping("/api/v1")
final class InboundMessageController {
    private final InboundMessageQueryService messages;
    private final CurrentPrincipalProvider principals;

    InboundMessageController(
            InboundMessageQueryService messages,
            CurrentPrincipalProvider principals
    ) {
        this.messages = messages;
        this.principals = principals;
    }

    @GetMapping("/inbound-envelopes")
    ResponseEntity<InboundEnvelopeQueuePage> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String processingStatus,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String resultType,
            @RequestParam(required = false) String resultId,
            @RequestParam(required = false) UUID canonicalMessageId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        InboundEnvelopeQueuePage page = messages.list(
                principals.current(),
                correlationId,
                new InboundEnvelopeQueueQuery(
                        projectId,
                        processingStatus,
                        messageType,
                        resultType,
                        resultId,
                        canonicalMessageId,
                        cursor,
                        limit));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(page);
    }

    @GetMapping("/inbound-envelopes/{envelopeId}")
    InboundEnvelopeView getEnvelope(
            @PathVariable UUID envelopeId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return messages.getEnvelope(principals.current(), correlationId, envelopeId);
    }

    @GetMapping("/canonical-messages/{messageId}")
    CanonicalMessageView getCanonicalMessage(
            @PathVariable UUID messageId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return messages.getCanonicalMessage(principals.current(), correlationId, messageId);
    }
}
