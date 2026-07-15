package com.serviceos.integration.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.shared.CorrelationIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
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
