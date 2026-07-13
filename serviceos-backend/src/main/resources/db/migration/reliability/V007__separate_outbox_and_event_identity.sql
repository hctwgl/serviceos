-- M8 的首版曾把 outbox_id 同时作为 eventId。新增独立列并保留历史 eventId，
-- 避免修改已提交 V001 checksum，也保证升级窗口内消费者去重键不变。
ALTER TABLE rel_outbox_event
    ADD COLUMN event_id uuid;

UPDATE rel_outbox_event
   SET event_id = outbox_id
 WHERE event_id IS NULL;

ALTER TABLE rel_outbox_event
    ALTER COLUMN event_id SET NOT NULL,
    ADD CONSTRAINT uq_rel_outbox_event_id UNIQUE (event_id);
