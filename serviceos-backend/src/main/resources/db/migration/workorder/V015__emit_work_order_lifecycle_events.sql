DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM wo_work_order LIMIT 1) THEN
        RAISE EXCEPTION
            'V015 requires an explicit WorkOrderReceived bootstrap plan for existing work orders';
    END IF;
END $$;

ALTER TABLE wo_work_order
    ADD COLUMN configuration_bundle_digest char(64) NOT NULL,
    ADD COLUMN activated_at timestamptz,
    ADD CONSTRAINT ck_wo_configuration_bundle_digest
        CHECK (configuration_bundle_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_wo_status
        CHECK (status IN ('RECEIVED', 'ACTIVE', 'SUSPENDED', 'FULFILLED', 'CANCELLED', 'CLOSED')),
    ADD CONSTRAINT ck_wo_activation
        CHECK (status = 'RECEIVED' OR activated_at IS NOT NULL);
