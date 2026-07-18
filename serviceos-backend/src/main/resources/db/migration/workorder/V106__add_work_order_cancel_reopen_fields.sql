-- M279：工单取消/重开审计字段；允许从未激活直接取消。

ALTER TABLE wo_work_order
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancel_reason_code varchar(64),
    ADD COLUMN cancel_approval_ref varchar(128),
    ADD COLUMN reopened_at timestamptz,
    ADD COLUMN reopen_approval_ref varchar(128);

ALTER TABLE wo_work_order
    DROP CONSTRAINT ck_wo_activation;

ALTER TABLE wo_work_order
    ADD CONSTRAINT ck_wo_activation CHECK (
        status = 'RECEIVED'
        OR activated_at IS NOT NULL
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    );

ALTER TABLE wo_work_order
    ADD CONSTRAINT ck_wo_cancel_state CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancel_reason_code IS NOT NULL)
        OR (status <> 'CANCELLED')
    );
