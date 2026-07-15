package com.serviceos.integration.infrastructure;

import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 入站幂等内核使用 PostgreSQL ON CONFLICT，而不是先查后插。
 *
 * <p>这是对 JDBC 的有意使用：Envelope transport key 和 Canonical business key 的并发判定必须
 * 依赖唯一约束，并检查每次状态更新的影响行数。</p>
 */
@Repository
final class JdbcInboundMessageRepository implements InboundMessageRepository {
    private final JdbcClient jdbc;

    JdbcInboundMessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EnvelopeRegistration registerEnvelope(NewInboundEnvelope envelope) {
        int inserted = jdbc.sql("""
                INSERT INTO int_inbound_envelope (
                    inbound_envelope_id, tenant_id, connector_version_id, message_type,
                    transport_dedup_key, external_message_id, received_at,
                    raw_payload_object_ref, raw_payload_digest, signature_status,
                    processing_status, correlation_id)
                VALUES (:id, :tenant, :connector, :messageType, :dedupKey, :externalMessageId,
                    :receivedAt, :objectRef, :rawDigest, 'VALID', 'RECEIVED', :correlationId)
                ON CONFLICT (tenant_id, connector_version_id, transport_dedup_key) DO NOTHING
                """)
                .param("id", envelope.inboundEnvelopeId()).param("tenant", envelope.tenantId())
                .param("connector", envelope.connectorVersionId())
                .param("messageType", envelope.messageType())
                .param("dedupKey", envelope.transportDedupKey())
                .param("externalMessageId", envelope.externalMessageId())
                .param("receivedAt", timestamptz(envelope.receivedAt()))
                .param("objectRef", envelope.rawPayloadObjectRef())
                .param("rawDigest", envelope.rawPayloadDigest())
                .param("correlationId", envelope.correlationId()).update();
        InboundEnvelopeRecord current = jdbc.sql(ENVELOPE_SELECT + """
                 WHERE tenant_id=:tenant AND connector_version_id=:connector
                   AND transport_dedup_key=:dedupKey
                """)
                .param("tenant", envelope.tenantId()).param("connector", envelope.connectorVersionId())
                .param("dedupKey", envelope.transportDedupKey())
                .query(this::envelope).single();
        return new EnvelopeRegistration(current, inserted == 1);
    }

    @Override
    public Optional<InboundEnvelopeRecord> findEnvelope(String tenantId, UUID envelopeId) {
        return jdbc.sql(ENVELOPE_SELECT + " WHERE tenant_id=:tenant AND inbound_envelope_id=:id")
                .param("tenant", tenantId).param("id", envelopeId)
                .query(this::envelope).optional();
    }

    @Override
    public boolean rejectEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            String resultCode,
            Instant completedAt
    ) {
        int updated = jdbc.sql("""
                UPDATE int_inbound_envelope
                   SET project_id=:projectId, canonical_payload_digest=:canonicalDigest,
                       mapping_version_id=:mappingVersion, processing_status='REJECTED',
                       result_code=:resultCode, completed_at=:completedAt
                 WHERE tenant_id=:tenant AND inbound_envelope_id=:id
                   AND processing_status='RECEIVED'
                """)
                .param("projectId", projectId).param("canonicalDigest", canonicalPayloadDigest)
                .param("mappingVersion", mappingVersionId).param("resultCode", resultCode)
                .param("completedAt", timestamptz(completedAt))
                .param("tenant", tenantId).param("id", envelopeId).update();
        if (updated == 1) {
            return true;
        }
        InboundEnvelopeRecord current = findEnvelope(tenantId, envelopeId)
                .orElseThrow(() -> new IllegalStateException("InboundEnvelope disappeared"));
        if (!"REJECTED".equals(current.view().processingStatus())
                || !resultCode.equals(current.view().resultCode())) {
            throw new IllegalStateException("InboundEnvelope rejection conflicts with existing result");
        }
        return false;
    }

    @Override
    public CanonicalRegistration registerCanonical(NewCanonicalMessage message) {
        int inserted = jdbc.sql("""
                INSERT INTO int_canonical_message (
                    canonical_message_id, tenant_id, project_id, connector_version_id,
                    message_type, business_key, payload_object_ref, payload_digest,
                    mapping_version_id, processing_status, source_envelope_id, created_at)
                VALUES (:id, :tenant, :projectId, :connector, :messageType, :businessKey,
                    :objectRef, :payloadDigest, :mappingVersion, 'PROCESSING', :sourceEnvelopeId,
                    :createdAt)
                ON CONFLICT (tenant_id, connector_version_id, message_type, business_key) DO NOTHING
                """)
                .param("id", message.canonicalMessageId()).param("tenant", message.tenantId())
                .param("projectId", message.projectId()).param("connector", message.connectorVersionId())
                .param("messageType", message.messageType()).param("businessKey", message.businessKey())
                .param("objectRef", message.payloadObjectRef()).param("payloadDigest", message.payloadDigest())
                .param("mappingVersion", message.mappingVersionId())
                .param("sourceEnvelopeId", message.sourceEnvelopeId())
                .param("createdAt", timestamptz(message.createdAt())).update();
        CanonicalMessageRecord current = jdbc.sql(CANONICAL_SELECT + """
                 WHERE tenant_id=:tenant AND connector_version_id=:connector
                   AND message_type=:messageType AND business_key=:businessKey
                """)
                .param("tenant", message.tenantId()).param("connector", message.connectorVersionId())
                .param("messageType", message.messageType()).param("businessKey", message.businessKey())
                .query(this::canonical).single();
        return new CanonicalRegistration(current, inserted == 1);
    }

    @Override
    public void completeCanonical(
            String tenantId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant processedAt
    ) {
        int updated = jdbc.sql("""
                UPDATE int_canonical_message
                   SET processing_status='COMPLETED', result_code=:resultCode,
                       result_type=:resultType, result_id=:resultId, processed_at=:processedAt
                 WHERE tenant_id=:tenant AND canonical_message_id=:id
                   AND processing_status='PROCESSING'
                """)
                .param("resultCode", resultCode).param("resultType", resultType)
                .param("resultId", resultId).param("processedAt", timestamptz(processedAt))
                .param("tenant", tenantId).param("id", canonicalMessageId).update();
        if (updated != 1) {
            throw new IllegalStateException("CanonicalMessage completion lost its PROCESSING state");
        }
    }

    @Override
    public void completeEnvelope(
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
    ) {
        int updated = jdbc.sql("""
                UPDATE int_inbound_envelope
                   SET project_id=:projectId, canonical_payload_digest=:canonicalDigest,
                       mapping_version_id=:mappingVersion, canonical_message_id=:canonicalId,
                       processing_status='COMPLETED', result_code=:resultCode,
                       result_type=:resultType, result_id=:resultId, completed_at=:completedAt
                 WHERE tenant_id=:tenant AND inbound_envelope_id=:id
                   AND processing_status='RECEIVED'
                """)
                .param("projectId", projectId).param("canonicalDigest", canonicalPayloadDigest)
                .param("mappingVersion", mappingVersionId).param("canonicalId", canonicalMessageId)
                .param("resultCode", resultCode).param("resultType", resultType).param("resultId", resultId)
                .param("completedAt", timestamptz(completedAt))
                .param("tenant", tenantId).param("id", envelopeId).update();
        if (updated == 0) {
            InboundEnvelopeRecord current = findEnvelope(tenantId, envelopeId)
                    .orElseThrow(() -> new IllegalStateException("InboundEnvelope disappeared"));
            if (!"COMPLETED".equals(current.view().processingStatus())
                    || !canonicalMessageId.equals(current.view().canonicalMessageId())) {
                throw new IllegalStateException("InboundEnvelope completion conflicts with existing result");
            }
        }
    }

    @Override
    public Optional<CanonicalMessageRecord> findCanonical(String tenantId, UUID canonicalMessageId) {
        return jdbc.sql(CANONICAL_SELECT + " WHERE tenant_id=:tenant AND canonical_message_id=:id")
                .param("tenant", tenantId).param("id", canonicalMessageId)
                .query(this::canonical).optional();
    }

    private InboundEnvelopeRecord envelope(ResultSet rs, int row) throws SQLException {
        UUID projectId = rs.getObject("project_id", UUID.class);
        UUID canonicalId = rs.getObject("canonical_message_id", UUID.class);
        return new InboundEnvelopeRecord(new InboundEnvelopeView(
                rs.getObject("inbound_envelope_id", UUID.class), projectId,
                rs.getString("connector_version_id"), rs.getString("message_type"),
                rs.getString("external_message_id"), rs.getString("raw_payload_digest"),
                rs.getString("canonical_payload_digest"), rs.getString("signature_status"),
                rs.getString("processing_status"), rs.getString("mapping_version_id"), canonicalId,
                rs.getString("result_code"), rs.getString("result_type"), rs.getString("result_id"),
                rs.getObject("received_at", java.time.OffsetDateTime.class).toInstant(),
                instant(rs, "completed_at"), rs.getString("correlation_id")),
                rs.getString("raw_payload_object_ref"), rs.getString("transport_dedup_key"));
    }

    private CanonicalMessageRecord canonical(ResultSet rs, int row) throws SQLException {
        return new CanonicalMessageRecord(new CanonicalMessageView(
                rs.getObject("canonical_message_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getString("connector_version_id"),
                rs.getString("message_type"), rs.getString("business_key"),
                rs.getString("payload_digest"), rs.getString("mapping_version_id"),
                rs.getString("processing_status"), rs.getString("result_code"),
                rs.getString("result_type"), rs.getString("result_id"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                instant(rs, "processed_at")), rs.getString("payload_object_ref"),
                rs.getObject("source_envelope_id", UUID.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static final String ENVELOPE_SELECT = """
            SELECT inbound_envelope_id, tenant_id, project_id, connector_version_id,
                   message_type, transport_dedup_key, external_message_id, received_at,
                   raw_payload_object_ref, raw_payload_digest, canonical_payload_digest,
                   signature_status, processing_status, mapping_version_id,
                   canonical_message_id, result_code, result_type, result_id,
                   correlation_id, completed_at
              FROM int_inbound_envelope
            """;

    private static final String CANONICAL_SELECT = """
            SELECT canonical_message_id, tenant_id, project_id, connector_version_id,
                   message_type, business_key, payload_object_ref, payload_digest,
                   mapping_version_id, processing_status, result_code, result_type,
                   result_id, source_envelope_id, created_at, processed_at
              FROM int_canonical_message
            """;
}
