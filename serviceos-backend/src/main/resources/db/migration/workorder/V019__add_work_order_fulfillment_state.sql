ALTER TABLE wo_work_order
    ADD COLUMN fulfilled_at timestamptz;

ALTER TABLE wo_work_order
    ADD CONSTRAINT ck_wo_fulfillment_state CHECK (
        (status = 'FULFILLED' AND fulfilled_at IS NOT NULL)
        OR (status <> 'FULFILLED' AND fulfilled_at IS NULL)
    );
