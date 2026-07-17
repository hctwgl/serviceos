-- M189：Admin 个人 SavedView。偏好事实归属 readmodel；不对 idn_/auth_ 建物理外键。
-- 不含分享表；visibility 固定为个人（PRIVATE 语义由 principal 作用域表达）。

CREATE TABLE rdm_saved_view (
    saved_view_id      uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    principal_id       varchar(128) NOT NULL,
    portal             varchar(32)  NOT NULL,
    page_id            varchar(120) NOT NULL,
    name               varchar(120) NOT NULL,
    schema_version     integer      NOT NULL,
    filter_json        jsonb        NOT NULL,
    sort_json          jsonb,
    column_json        jsonb,
    is_default         boolean      NOT NULL,
    aggregate_version  bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_saved_view PRIMARY KEY (saved_view_id),
    CONSTRAINT ck_rdm_saved_view_portal CHECK (portal = 'ADMIN'),
    CONSTRAINT ck_rdm_saved_view_page CHECK (length(btrim(page_id)) > 0),
    CONSTRAINT ck_rdm_saved_view_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_rdm_saved_view_schema CHECK (schema_version > 0),
    CONSTRAINT ck_rdm_saved_view_version CHECK (aggregate_version >= 1),
    CONSTRAINT uq_rdm_saved_view_owner_page_name UNIQUE (tenant_id, principal_id, portal, page_id, name)
);

CREATE INDEX ix_rdm_saved_view_owner_page
    ON rdm_saved_view (tenant_id, principal_id, portal, page_id, updated_at DESC);

-- 同一主体+页面最多一个默认视图
CREATE UNIQUE INDEX uq_rdm_saved_view_owner_page_default
    ON rdm_saved_view (tenant_id, principal_id, portal, page_id)
    WHERE is_default;
