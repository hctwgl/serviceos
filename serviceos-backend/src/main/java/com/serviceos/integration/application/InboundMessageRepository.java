package com.serviceos.integration.application;

import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 入站消息持久化端口。
 *
 * <p>注册方法必须用数据库唯一键处理并发；相同 transport/business key 的重复请求不得先读后写。</p>
 */
public interface InboundMessageRepository {
    EnvelopeRegistration registerEnvelope(NewInboundEnvelope envelope);

    Optional<InboundEnvelopeRecord> findEnvelope(String tenantId, UUID envelopeId);

    boolean rejectEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            String resultCode,
            Instant completedAt
    );

    CanonicalRegistration registerCanonical(NewCanonicalMessage message);

    void completeCanonical(
            String tenantId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant processedAt
    );

    void completeEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant completedAt
    );

    Optional<CanonicalMessageRecord> findCanonical(String tenantId, UUID canonicalMessageId);

    record EnvelopeRegistration(InboundEnvelopeRecord envelope, boolean created) {
    }

    record CanonicalRegistration(CanonicalMessageRecord message, boolean created) {
    }

    record NewInboundEnvelope(
            UUID inboundEnvelopeId,
            String tenantId,
            String connectorVersionId,
            String messageType,
            String transportDedupKey,
            String externalMessageId,
            Instant receivedAt,
            String rawPayloadObjectRef,
            String rawPayloadDigest,
            String correlationId
    ) {
    }

    record NewCanonicalMessage(
            UUID canonicalMessageId,
            String tenantId,
            UUID projectId,
            String connectorVersionId,
            String messageType,
            String businessKey,
            String payloadObjectRef,
            String payloadDigest,
            String mappingVersionId,
            UUID sourceEnvelopeId,
            Instant createdAt
    ) {
    }

    record InboundEnvelopeRecord(
            InboundEnvelopeView view,
            String rawPayloadObjectRef,
            String transportDedupKey
    ) {
    }

    record CanonicalMessageRecord(
            CanonicalMessageView view,
            String payloadObjectRef,
            UUID sourceEnvelopeId
    ) {
    }
}
