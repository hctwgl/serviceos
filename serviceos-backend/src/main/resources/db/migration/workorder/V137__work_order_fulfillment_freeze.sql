-- M378：工单冻结项目履约 Profile Revision；历史工单默认 LEGACY_BUNDLE。

ALTER TABLE wo_work_order
    ADD COLUMN IF NOT EXISTS fulfillment_config_kind varchar(32) NOT NULL DEFAULT 'LEGACY_BUNDLE',
    ADD COLUMN IF NOT EXISTS fulfillment_profile_id uuid,
    ADD COLUMN IF NOT EXISTS fulfillment_revision_id uuid,
    ADD COLUMN IF NOT EXISTS fulfillment_version varchar(64);

ALTER TABLE wo_work_order
    DROP CONSTRAINT IF EXISTS ck_wo_fulfillment_config_kind;

ALTER TABLE wo_work_order
    ADD CONSTRAINT ck_wo_fulfillment_config_kind CHECK (
        fulfillment_config_kind IN ('PROFILE_REVISION', 'LEGACY_BUNDLE')
    );

ALTER TABLE wo_work_order
    DROP CONSTRAINT IF EXISTS ck_wo_fulfillment_freeze_shape;

ALTER TABLE wo_work_order
    ADD CONSTRAINT ck_wo_fulfillment_freeze_shape CHECK (
        (fulfillment_config_kind = 'LEGACY_BUNDLE'
            AND fulfillment_profile_id IS NULL
            AND fulfillment_revision_id IS NULL
            AND fulfillment_version IS NULL)
        OR (fulfillment_config_kind = 'PROFILE_REVISION'
            AND fulfillment_profile_id IS NOT NULL
            AND fulfillment_revision_id IS NOT NULL
            AND fulfillment_version IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS ix_wo_fulfillment_revision
    ON wo_work_order (tenant_id, fulfillment_revision_id)
    WHERE fulfillment_revision_id IS NOT NULL;
