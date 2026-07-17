-- M190：Admin 个人 UI Preference。偏好事实归属 readmodel；不对 idn_/auth_ 建物理外键。
-- portal 固定 ADMIN；preference_key 白名单由应用层强制。

CREATE TABLE rdm_ui_preference (
    tenant_id          varchar(64)  NOT NULL,
    principal_id       varchar(128) NOT NULL,
    portal             varchar(32)  NOT NULL,
    preference_key     varchar(64)  NOT NULL,
    value_json         jsonb        NOT NULL,
    schema_version     integer      NOT NULL,
    aggregate_version  bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_ui_preference PRIMARY KEY (tenant_id, principal_id, portal, preference_key),
    CONSTRAINT ck_rdm_ui_preference_portal CHECK (portal = 'ADMIN'),
    CONSTRAINT ck_rdm_ui_preference_key CHECK (length(btrim(preference_key)) > 0),
    CONSTRAINT ck_rdm_ui_preference_schema CHECK (schema_version > 0),
    CONSTRAINT ck_rdm_ui_preference_version CHECK (aggregate_version >= 1)
);

CREATE INDEX ix_rdm_ui_preference_owner
    ON rdm_ui_preference (tenant_id, principal_id, portal, updated_at DESC);
