-- M286：项目级 Bundle 通道激活（STABLE/CANARY）；不修改已发布 Bundle 内容。

CREATE TABLE cfg_bundle_channel_activation (
    activation_id          uuid         NOT NULL,
    tenant_id              varchar(64)  NOT NULL,
    project_id             uuid         NOT NULL,
    channel                varchar(16)  NOT NULL,
    bundle_id              uuid         NOT NULL,
    previous_activation_id uuid,
    status                 varchar(16)  NOT NULL,
    approval_ref           varchar(128) NOT NULL,
    activated_by           varchar(128) NOT NULL,
    activated_at           timestamptz  NOT NULL,
    superseded_at          timestamptz,
    aggregate_version      bigint       NOT NULL,
    CONSTRAINT pk_cfg_bundle_channel_activation PRIMARY KEY (activation_id),
    CONSTRAINT uq_cfg_activation_tenant_id UNIQUE (tenant_id, activation_id),
    CONSTRAINT fk_cfg_activation_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES prj_project (tenant_id, project_id),
    CONSTRAINT fk_cfg_activation_bundle
        FOREIGN KEY (tenant_id, bundle_id) REFERENCES cfg_configuration_bundle (tenant_id, bundle_id),
    CONSTRAINT fk_cfg_activation_previous
        FOREIGN KEY (previous_activation_id) REFERENCES cfg_bundle_channel_activation (activation_id),
    CONSTRAINT ck_cfg_activation_channel CHECK (channel IN ('STABLE', 'CANARY')),
    CONSTRAINT ck_cfg_activation_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_cfg_activation_version CHECK (aggregate_version >= 1),
    CONSTRAINT ck_cfg_activation_supersede CHECK (
        (status = 'ACTIVE' AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED' AND superseded_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_cfg_active_channel_per_project
    ON cfg_bundle_channel_activation (tenant_id, project_id, channel)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_cfg_activation_project_channel
    ON cfg_bundle_channel_activation (tenant_id, project_id, channel, activated_at DESC);
