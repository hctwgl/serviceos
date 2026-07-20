-- M378：项目工单类型履约配置 Profile / Revision（编排层，不替代 Bundle/资产引擎）。

CREATE TABLE cfg_project_fulfillment_profile (
    profile_id              uuid         NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    project_id              uuid         NOT NULL,
    service_product_code    varchar(96)  NOT NULL,
    profile_name            varchar(200) NOT NULL,
    description             varchar(2000),
    status                  varchar(16)  NOT NULL,
    active_revision_id      uuid,
    draft_revision_id       uuid,
    aggregate_version       bigint       NOT NULL,
    created_by              varchar(128) NOT NULL,
    updated_by              varchar(128) NOT NULL,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    CONSTRAINT pk_cfg_pfp PRIMARY KEY (profile_id),
    CONSTRAINT uq_cfg_pfp_tenant_id UNIQUE (tenant_id, profile_id),
    CONSTRAINT uq_cfg_pfp_project_product UNIQUE (tenant_id, project_id, service_product_code),
    CONSTRAINT fk_cfg_pfp_project
        FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project (tenant_id, project_id),
    CONSTRAINT ck_cfg_pfp_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_cfg_pfp_version CHECK (aggregate_version >= 1),
    CONSTRAINT ck_cfg_pfp_active_consistency CHECK (
        (status = 'DRAFT' AND active_revision_id IS NULL)
        OR (status <> 'DRAFT')
    )
);

CREATE TABLE cfg_project_fulfillment_revision (
    revision_id                 uuid         NOT NULL,
    tenant_id                   varchar(64)  NOT NULL,
    profile_id                  uuid         NOT NULL,
    version_no                  integer      NOT NULL,
    revision_status             varchar(16)  NOT NULL,
    document_json               jsonb        NOT NULL,
    source_bundle_id            uuid,
    workflow_asset_version_id   uuid,
    manifest_json               jsonb,
    validation_json             jsonb,
    content_digest              char(64),
    effective_from              timestamptz,
    effective_to                timestamptz,
    supersedes_revision_id      uuid,
    published_by                varchar(128),
    published_at                timestamptz,
    created_at                  timestamptz  NOT NULL,
    CONSTRAINT pk_cfg_pfr PRIMARY KEY (revision_id),
    CONSTRAINT uq_cfg_pfr_tenant_id UNIQUE (tenant_id, revision_id),
    CONSTRAINT fk_cfg_pfr_profile
        FOREIGN KEY (tenant_id, profile_id)
        REFERENCES cfg_project_fulfillment_profile (tenant_id, profile_id),
    CONSTRAINT fk_cfg_pfr_bundle
        FOREIGN KEY (tenant_id, source_bundle_id)
        REFERENCES cfg_configuration_bundle (tenant_id, bundle_id),
    CONSTRAINT fk_cfg_pfr_supersedes
        FOREIGN KEY (supersedes_revision_id)
        REFERENCES cfg_project_fulfillment_revision (revision_id),
    CONSTRAINT ck_cfg_pfr_status CHECK (
        revision_status IN ('DRAFT', 'PUBLISHED')
    ),
    CONSTRAINT ck_cfg_pfr_version_no CHECK (version_no >= 0),
    CONSTRAINT ck_cfg_pfr_digest CHECK (
        content_digest IS NULL OR content_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_cfg_pfr_validity CHECK (
        effective_to IS NULL
        OR effective_from IS NULL
        OR effective_to > effective_from
    ),
    CONSTRAINT ck_cfg_pfr_published_shape CHECK (
        (revision_status = 'DRAFT'
            AND manifest_json IS NULL
            AND content_digest IS NULL
            AND published_at IS NULL
            AND published_by IS NULL)
        OR (revision_status = 'PUBLISHED'
            AND manifest_json IS NOT NULL
            AND content_digest IS NOT NULL
            AND published_at IS NOT NULL
            AND published_by IS NOT NULL
            AND effective_from IS NOT NULL
            AND version_no >= 1)
    )
);

-- active/draft revision 指针由应用同事务维护；不建指向 revision 的外键，避免与
-- revision→profile 外键形成循环插入依赖。指针完整性由服务与 IT 证明。

-- 每个 Profile 最多一个开放草稿。
CREATE UNIQUE INDEX uq_cfg_pfr_open_draft
    ON cfg_project_fulfillment_revision (profile_id)
    WHERE revision_status = 'DRAFT';

-- 已发布版本号在 Profile 内唯一。
CREATE UNIQUE INDEX uq_cfg_pfr_published_version
    ON cfg_project_fulfillment_revision (profile_id, version_no)
    WHERE revision_status = 'PUBLISHED';

-- 同一项目+服务产品下，已发布生效窗口不得重叠（用 exclusion 约束）。
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE cfg_project_fulfillment_revision
    ADD CONSTRAINT ex_cfg_pfr_effective_window
    EXCLUDE USING gist (
        tenant_id WITH =,
        profile_id WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
    )
    WHERE (revision_status = 'PUBLISHED');

CREATE INDEX ix_cfg_pfp_project
    ON cfg_project_fulfillment_profile (tenant_id, project_id, status);

CREATE INDEX ix_cfg_pfr_profile_published
    ON cfg_project_fulfillment_revision (tenant_id, profile_id, effective_from DESC)
    WHERE revision_status = 'PUBLISHED';

-- 已发布 Revision 不可变。
CREATE OR REPLACE FUNCTION cfg_reject_published_fulfillment_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.revision_status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'published project fulfillment revision is immutable';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.revision_status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'published project fulfillment revision is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cfg_pfr_published_immutable
    BEFORE UPDATE OR DELETE ON cfg_project_fulfillment_revision
    FOR EACH ROW EXECUTE FUNCTION cfg_reject_published_fulfillment_revision_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('project.fulfillment.read', '查看项目履约配置', 'NORMAL', now()),
    ('project.fulfillment.create', '创建项目履约配置', 'HIGH', now()),
    ('project.fulfillment.draft.write', '编辑项目履约配置草稿', 'NORMAL', now()),
    ('project.fulfillment.validate', '校验项目履约配置', 'NORMAL', now()),
    ('project.fulfillment.publish', '发布项目履约配置版本', 'HIGH', now()),
    ('project.fulfillment.suspend', '暂停项目履约配置', 'HIGH', now()),
    ('project.fulfillment.resume', '恢复项目履约配置', 'HIGH', now()),
    ('project.fulfillment.revision.read', '查看项目履约配置版本', 'NORMAL', now()),
    ('project.fulfillment.snapshot.read', '查看工单履约配置快照', 'NORMAL', now()),
    ('project.fulfillment.techRef.read', '查看履约配置技术引用', 'NORMAL', now())
ON CONFLICT (capability_code) DO NOTHING;
