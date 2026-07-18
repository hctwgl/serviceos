-- M290：CANARY 多槽位；ACTIVE 唯一键扩展为 (tenant, project, channel, slot_code)。

ALTER TABLE cfg_bundle_channel_activation
    ADD COLUMN slot_code varchar(64) NOT NULL DEFAULT 'primary';

ALTER TABLE cfg_bundle_channel_activation
    ADD CONSTRAINT ck_cfg_activation_slot_code CHECK (
        slot_code ~ '^[a-z][a-z0-9_-]{0,63}$'
    );

ALTER TABLE cfg_bundle_channel_activation
    ADD CONSTRAINT ck_cfg_activation_stable_slot CHECK (
        channel <> 'STABLE' OR slot_code = 'primary'
    );

DROP INDEX IF EXISTS uq_cfg_active_channel_per_project;

CREATE UNIQUE INDEX uq_cfg_active_channel_slot_per_project
    ON cfg_bundle_channel_activation (tenant_id, project_id, channel, slot_code)
    WHERE status = 'ACTIVE';
