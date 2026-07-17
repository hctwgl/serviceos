-- M137：为 Admin 试点出站夹具登记 BYD CREATE_WORK_ORDER Envelope/Canonical 系谱。
-- 仅本地开发库；不伪造生产凭据，不绕过 outbound 失败关闭语义。
\set ON_ERROR_STOP on

INSERT INTO int_inbound_envelope (
    inbound_envelope_id, tenant_id, project_id, connector_version_id,
    message_type, transport_dedup_key, external_message_id, received_at,
    raw_payload_object_ref, raw_payload_digest, canonical_payload_digest,
    signature_status, processing_status, mapping_version_id, result_code,
    result_type, result_id, correlation_id, completed_at
) VALUES (
    :'outbound_envelope_id', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'byd-cpim-v7.3.1', 'CREATE_WORK_ORDER',
    :'outbound_transport_dedup', :'outbound_external_message_id', now(),
    :'outbound_object_ref', :'outbound_payload_digest', :'outbound_payload_digest',
    'VALID', 'COMPLETED', 'byd-ocean-shandong-install-v1', 'ACCEPTED',
    'WORK_ORDER', :'outbound_work_order_id', :'outbound_correlation_id', now()
);

INSERT INTO int_canonical_message (
    canonical_message_id, tenant_id, project_id, connector_version_id,
    message_type, business_key, payload_object_ref, payload_digest,
    mapping_version_id, processing_status, result_code, result_type,
    result_id, source_envelope_id, created_at, processed_at
) VALUES (
    :'outbound_canonical_id', 'tenant-local',
    '10000000-0000-4000-8000-000000000001', 'byd-cpim-v7.3.1', 'CREATE_WORK_ORDER',
    :'outbound_business_key', :'outbound_object_ref', :'outbound_payload_digest',
    'byd-ocean-shandong-install-v1', 'COMPLETED', 'ACCEPTED', 'WORK_ORDER',
    :'outbound_work_order_id', :'outbound_envelope_id', now(), now()
);
