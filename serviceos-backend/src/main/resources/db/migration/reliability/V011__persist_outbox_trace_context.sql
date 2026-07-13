-- Outbox 跨进程/跨时间发布时必须恢复原始 API 或任务 Trace；只保存 W3C 标准头，不保存 baggage。
ALTER TABLE rel_outbox_event
    ADD COLUMN trace_parent varchar(55),
    ADD COLUMN trace_state  varchar(512);

ALTER TABLE rel_outbox_event
    ADD CONSTRAINT ck_rel_outbox_trace_parent
        CHECK (trace_parent IS NULL OR trace_parent ~ '^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$'),
    ADD CONSTRAINT ck_rel_outbox_trace_state_length
        CHECK (trace_state IS NULL OR char_length(trace_state) <= 512);
