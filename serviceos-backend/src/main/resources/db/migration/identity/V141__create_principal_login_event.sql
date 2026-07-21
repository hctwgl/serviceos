-- M403：主体最近登录事实。仅记录成功 OIDC 解析；不存密码、不存 subject 明文。
-- issuer 仅作登录渠道展示；列表接口仍需 identity.read，不替代 identity.readSensitive 绑定详情。

CREATE TABLE idn_principal_login_event (
    login_event_id   uuid         NOT NULL,
    tenant_id        varchar(64)  NOT NULL,
    principal_id     uuid         NOT NULL,
    client_id        varchar(128) NOT NULL,
    issuer           varchar(512) NOT NULL,
    auth_channel     varchar(32)  NOT NULL,
    outcome          varchar(24)  NOT NULL,
    correlation_id   varchar(128) NOT NULL,
    occurred_at      timestamptz  NOT NULL,
    CONSTRAINT pk_idn_principal_login_event PRIMARY KEY (login_event_id),
    CONSTRAINT ck_idn_principal_login_channel CHECK (auth_channel = 'OIDC'),
    CONSTRAINT ck_idn_principal_login_outcome CHECK (outcome = 'SUCCEEDED'),
    CONSTRAINT ck_idn_principal_login_client CHECK (length(btrim(client_id)) > 0),
    CONSTRAINT ck_idn_principal_login_issuer CHECK (length(btrim(issuer)) > 0)
);

CREATE INDEX ix_idn_principal_login_owner_time
    ON idn_principal_login_event (tenant_id, principal_id, occurred_at DESC);
