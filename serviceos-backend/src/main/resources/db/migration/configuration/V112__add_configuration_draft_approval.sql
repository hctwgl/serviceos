-- M285：草稿审批门禁与开放草稿唯一索引扩展。

ALTER TABLE cfg_configuration_asset_draft
    ADD COLUMN approval_ref varchar(128),
    ADD COLUMN approved_by varchar(128),
    ADD COLUMN approved_at timestamptz;

ALTER TABLE cfg_configuration_asset_draft
    DROP CONSTRAINT ck_cfg_draft_status;

ALTER TABLE cfg_configuration_asset_draft
    ADD CONSTRAINT ck_cfg_draft_status CHECK (
        status IN ('DRAFT', 'VALIDATED', 'APPROVED', 'PUBLISHED', 'DISCARDED')
    );

ALTER TABLE cfg_configuration_asset_draft
    ADD CONSTRAINT ck_cfg_draft_approval CHECK (
        (status = 'APPROVED' AND approval_ref IS NOT NULL AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL)
        OR (status = 'PUBLISHED')
        OR (status IN ('DRAFT', 'VALIDATED', 'DISCARDED')
            AND approval_ref IS NULL AND approved_by IS NULL AND approved_at IS NULL)
    );

DROP INDEX IF EXISTS uq_cfg_open_draft_per_key;

CREATE UNIQUE INDEX uq_cfg_open_draft_per_key
    ON cfg_configuration_asset_draft (
        tenant_id, asset_type, asset_key, intended_semantic_version
    )
    WHERE status IN ('DRAFT', 'VALIDATED', 'APPROVED');
