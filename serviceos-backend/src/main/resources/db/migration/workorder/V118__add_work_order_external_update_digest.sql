-- M302：记录最近一次外部详情更新摘要，支撑同载荷重放而不改动建单 payload_digest。

ALTER TABLE wo_work_order
    ADD COLUMN last_external_update_digest char(64);

ALTER TABLE wo_work_order
    ADD CONSTRAINT ck_wo_last_external_update_digest CHECK (
        last_external_update_digest IS NULL
        OR last_external_update_digest ~ '^[0-9a-f]{64}$'
    );
