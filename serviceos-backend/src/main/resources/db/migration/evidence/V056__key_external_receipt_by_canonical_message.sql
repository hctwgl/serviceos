-- M57：一个 transport Envelope 可拆分多个 CanonicalMessage；回执幂等必须由逐项 Canonical 标识拥有。

ALTER TABLE evd_external_review_receipt
    DROP CONSTRAINT uq_evd_external_receipt_envelope;

ALTER TABLE evd_external_review_receipt
    ADD CONSTRAINT uq_evd_external_receipt_canonical
        UNIQUE (tenant_id, canonical_message_id);

CREATE INDEX ix_evd_external_receipt_envelope
    ON evd_external_review_receipt (tenant_id, inbound_envelope_id, received_at DESC);

COMMENT ON COLUMN evd_external_review_receipt.inbound_envelope_id IS
    '传输 Envelope；同一批次可对应多个逐项回执，不再作为结果幂等键';
COMMENT ON COLUMN evd_external_review_receipt.canonical_message_id IS
    '逐业务项 CanonicalMessage；M57 起为 ExternalReviewReceipt 权威幂等键';
