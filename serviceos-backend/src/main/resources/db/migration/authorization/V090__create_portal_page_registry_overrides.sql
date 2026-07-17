-- M188：Page Registry 租户覆盖与 feature gate。
-- pageId/route/requiredCapabilities 仍由代码注册；本表只管理启用、标题、排序与 gate。
-- auth_* 不对 idn_/org_/net_ 建立物理外键。

CREATE TABLE auth_feature_gate (
    tenant_id   varchar(64)  NOT NULL,
    gate_code   varchar(120) NOT NULL,
    enabled     boolean      NOT NULL,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT pk_auth_feature_gate PRIMARY KEY (tenant_id, gate_code),
    CONSTRAINT ck_auth_feature_gate_code CHECK (length(btrim(gate_code)) > 0)
);

CREATE TABLE auth_page_registry_override (
    tenant_id       varchar(64)  NOT NULL,
    page_id         varchar(120) NOT NULL,
    enabled         boolean      NOT NULL,
    title_override  varchar(200),
    sort_order      integer,
    feature_gate    varchar(120),
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_auth_page_registry_override PRIMARY KEY (tenant_id, page_id),
    CONSTRAINT ck_auth_page_registry_override_page CHECK (length(btrim(page_id)) > 0),
    CONSTRAINT ck_auth_page_registry_override_title CHECK (
        title_override IS NULL OR length(btrim(title_override)) > 0
    ),
    CONSTRAINT ck_auth_page_registry_override_order CHECK (
        sort_order IS NULL OR sort_order >= 0
    )
);

CREATE INDEX ix_auth_page_registry_override_tenant
    ON auth_page_registry_override (tenant_id, enabled, sort_order);

-- Network/Technician Portal 导航所需稳定能力；不宣称完整 Portal 业务已交付。
INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('networkTask.read', '读取本网点任务导航摘要', 'NORMAL', now()),
    ('technician.readOwnNetwork', '读取本网点师傅导航摘要', 'NORMAL', now()),
    ('task.readAssigned', '读取本人指派任务导航摘要', 'NORMAL', now())
ON CONFLICT (capability_code) DO NOTHING;
