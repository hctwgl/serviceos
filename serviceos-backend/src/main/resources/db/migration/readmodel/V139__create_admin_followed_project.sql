-- M401：Admin 个人关注项目。偏好事实归属 readmodel；不对 idn_/auth_/prj_ 建物理外键。
-- portal 固定 ADMIN；列表读取时按 project.read 重新鉴权并清理失权项。

CREATE TABLE rdm_followed_project (
    tenant_id     varchar(64)  NOT NULL,
    principal_id  varchar(128) NOT NULL,
    portal        varchar(32)  NOT NULL,
    project_id    uuid         NOT NULL,
    display_ref   varchar(120) NOT NULL,
    followed_at   timestamptz  NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_rdm_followed_project
        PRIMARY KEY (tenant_id, principal_id, portal, project_id),
    CONSTRAINT ck_rdm_followed_project_portal CHECK (portal = 'ADMIN'),
    CONSTRAINT ck_rdm_followed_project_display CHECK (length(btrim(display_ref)) > 0)
);

CREATE INDEX ix_rdm_followed_project_owner_followed
    ON rdm_followed_project (tenant_id, principal_id, portal, followed_at DESC);
