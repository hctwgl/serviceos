-- M193：Admin 个人最近访问。偏好事实归属 readmodel；不对 idn_/auth_ 建物理外键。
-- portal 固定 ADMIN；resource_type 白名单由应用层强制；display_ref 禁止敏感全文。

CREATE TABLE rdm_recent_resource (
    tenant_id        varchar(64)  NOT NULL,
    principal_id     varchar(128) NOT NULL,
    portal           varchar(32)  NOT NULL,
    resource_type    varchar(32)  NOT NULL,
    resource_id      varchar(128) NOT NULL,
    page_id          varchar(64),
    display_ref      varchar(120) NOT NULL,
    last_visited_at  timestamptz  NOT NULL,
    created_at       timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_recent_resource
        PRIMARY KEY (tenant_id, principal_id, portal, resource_type, resource_id),
    CONSTRAINT ck_rdm_recent_resource_portal CHECK (portal = 'ADMIN'),
    CONSTRAINT ck_rdm_recent_resource_type CHECK (length(btrim(resource_type)) > 0),
    CONSTRAINT ck_rdm_recent_resource_id CHECK (length(btrim(resource_id)) > 0),
    CONSTRAINT ck_rdm_recent_resource_display CHECK (length(btrim(display_ref)) > 0)
);

CREATE INDEX ix_rdm_recent_resource_owner_visited
    ON rdm_recent_resource (tenant_id, principal_id, portal, last_visited_at DESC);
