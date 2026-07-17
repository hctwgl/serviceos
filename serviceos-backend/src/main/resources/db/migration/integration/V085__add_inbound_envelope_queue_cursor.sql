-- M158：跨项目入站 Envelope 队列按 processing_status + receivedAt DESC + id 稳定分页。

CREATE INDEX ix_int_inbound_envelope_queue_cursor
    ON int_inbound_envelope (
        tenant_id,
        processing_status,
        received_at DESC,
        inbound_envelope_id DESC
    );

COMMENT ON INDEX ix_int_inbound_envelope_queue_cursor IS
    'M158：入站 Envelope 授权队列范围化倒序稳定游标';
