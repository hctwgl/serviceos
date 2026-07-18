-- M282：配置资产草稿（设计器工作副本）；已发布版本仍不可变。

CREATE TABLE cfg_configuration_asset_draft (
    draft_id                   uuid         NOT NULL,
    tenant_id                  varchar(64)  NOT NULL,
    asset_type                 varchar(32)  NOT NULL,
    asset_key                  varchar(128) NOT NULL,
    intended_semantic_version  varchar(64)  NOT NULL,
    schema_version             varchar(64)  NOT NULL,
    definition                 jsonb        NOT NULL,
    content_digest             char(64)     NOT NULL,
    status                     varchar(16)  NOT NULL,
    base_version_id            uuid,
    published_version_id       uuid,
    validation_errors          jsonb,
    aggregate_version          bigint       NOT NULL,
    created_by                 varchar(128) NOT NULL,
    updated_by                 varchar(128) NOT NULL,
    created_at                 timestamptz  NOT NULL,
    updated_at                 timestamptz  NOT NULL,
    CONSTRAINT pk_cfg_asset_draft PRIMARY KEY (draft_id),
    CONSTRAINT uq_cfg_asset_draft_tenant_id UNIQUE (tenant_id, draft_id),
    CONSTRAINT ck_cfg_draft_asset_type CHECK (asset_type IN (
        'WORKFLOW', 'FORM', 'EVIDENCE', 'RULE', 'SLA', 'DISPATCH', 'PRICING',
        'INTEGRATION', 'ASSIGNEE_POLICY', 'NOTIFICATION'
    )),
    CONSTRAINT ck_cfg_draft_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cfg_draft_status CHECK (
        status IN ('DRAFT', 'VALIDATED', 'PUBLISHED', 'DISCARDED')
    ),
    CONSTRAINT ck_cfg_draft_version CHECK (aggregate_version >= 1),
    CONSTRAINT ck_cfg_draft_publish_link CHECK (
        (status = 'PUBLISHED' AND published_version_id IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_version_id IS NULL)
    ),
    CONSTRAINT fk_cfg_draft_base_version
        FOREIGN KEY (base_version_id) REFERENCES cfg_configuration_asset_version (version_id),
    CONSTRAINT fk_cfg_draft_published_version
        FOREIGN KEY (published_version_id) REFERENCES cfg_configuration_asset_version (version_id)
);

CREATE UNIQUE INDEX uq_cfg_open_draft_per_key
    ON cfg_configuration_asset_draft (
        tenant_id, asset_type, asset_key, intended_semantic_version
    )
    WHERE status IN ('DRAFT', 'VALIDATED');

CREATE INDEX ix_cfg_draft_tenant_type_updated
    ON cfg_configuration_asset_draft (tenant_id, asset_type, updated_at DESC);
