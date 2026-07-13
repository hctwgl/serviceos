ALTER TABLE prj_project
    ADD CONSTRAINT uq_prj_project_tenant_id UNIQUE (tenant_id, project_id);

CREATE TABLE cfg_configuration_asset_version (
    version_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    asset_type varchar(32) NOT NULL,
    asset_key varchar(128) NOT NULL,
    semantic_version varchar(64) NOT NULL,
    schema_version varchar(64) NOT NULL,
    definition jsonb NOT NULL,
    content_digest char(64) NOT NULL,
    status varchar(16) NOT NULL,
    published_at timestamptz NOT NULL,
    CONSTRAINT uq_cfg_asset_version UNIQUE (tenant_id, asset_type, asset_key, semantic_version),
    CONSTRAINT uq_cfg_asset_scope_identity
        UNIQUE (tenant_id, version_id, asset_type, content_digest),
    CONSTRAINT ck_cfg_asset_type CHECK (asset_type IN (
        'WORKFLOW', 'FORM', 'EVIDENCE', 'RULE', 'SLA', 'DISPATCH', 'PRICING',
        'INTEGRATION', 'ASSIGNEE_POLICY', 'NOTIFICATION'
    )),
    CONSTRAINT ck_cfg_asset_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cfg_asset_status CHECK (status = 'PUBLISHED')
);

CREATE TABLE cfg_configuration_bundle (
    bundle_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    bundle_code varchar(128) NOT NULL,
    bundle_version varchar(64) NOT NULL,
    brand_code varchar(64) NOT NULL,
    service_product_code varchar(96) NOT NULL,
    province_code varchar(16),
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    manifest_digest char(64) NOT NULL,
    status varchar(16) NOT NULL,
    published_at timestamptz NOT NULL,
    CONSTRAINT uq_cfg_bundle_version UNIQUE (tenant_id, bundle_code, bundle_version),
    CONSTRAINT uq_cfg_bundle_tenant_id UNIQUE (tenant_id, bundle_id),
    CONSTRAINT uq_cfg_bundle_tenant_project_id UNIQUE (tenant_id, project_id, bundle_id),
    CONSTRAINT fk_cfg_bundle_project_scope FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project(tenant_id, project_id),
    CONSTRAINT ck_cfg_bundle_validity CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_cfg_bundle_digest CHECK (manifest_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cfg_bundle_status CHECK (status = 'PUBLISHED')
);

CREATE TABLE cfg_configuration_bundle_item (
    tenant_id varchar(64) NOT NULL,
    bundle_id uuid NOT NULL,
    asset_type varchar(32) NOT NULL,
    asset_version_id uuid NOT NULL,
    content_digest char(64) NOT NULL,
    PRIMARY KEY (bundle_id, asset_type),
    CONSTRAINT uq_cfg_bundle_asset_version UNIQUE (bundle_id, asset_version_id),
    CONSTRAINT fk_cfg_bundle_item_bundle_scope
        FOREIGN KEY (tenant_id, bundle_id)
        REFERENCES cfg_configuration_bundle(tenant_id, bundle_id),
    CONSTRAINT fk_cfg_bundle_item_asset_identity
        FOREIGN KEY (tenant_id, asset_version_id, asset_type, content_digest)
        REFERENCES cfg_configuration_asset_version(
            tenant_id, version_id, asset_type, content_digest
        ),
    CONSTRAINT ck_cfg_bundle_item_digest CHECK (content_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_cfg_bundle_resolution
    ON cfg_configuration_bundle (
        tenant_id, project_id, brand_code, service_product_code,
        province_code, effective_from, effective_until
    ) WHERE status = 'PUBLISHED';

-- Published 配置是历史工单的权威输入，数据库层禁止原地更新或删除。
CREATE OR REPLACE FUNCTION cfg_reject_published_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'published configuration is immutable';
END;
$$;

CREATE TRIGGER trg_cfg_asset_version_immutable
    BEFORE UPDATE OR DELETE ON cfg_configuration_asset_version
    FOR EACH ROW EXECUTE FUNCTION cfg_reject_published_mutation();

CREATE TRIGGER trg_cfg_bundle_immutable
    BEFORE UPDATE OR DELETE ON cfg_configuration_bundle
    FOR EACH ROW EXECUTE FUNCTION cfg_reject_published_mutation();

CREATE TRIGGER trg_cfg_bundle_item_immutable
    BEFORE UPDATE OR DELETE ON cfg_configuration_bundle_item
    FOR EACH ROW EXECUTE FUNCTION cfg_reject_published_mutation();
