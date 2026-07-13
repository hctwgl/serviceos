CREATE TABLE auth_capability (
    capability_code varchar(120) NOT NULL,
    capability_name varchar(200) NOT NULL,
    risk_level      varchar(24)  NOT NULL DEFAULT 'NORMAL',
    created_at      timestamptz  NOT NULL,
    CONSTRAINT pk_auth_capability PRIMARY KEY (capability_code),
    CONSTRAINT ck_auth_capability_risk CHECK (risk_level IN ('NORMAL', 'HIGH', 'CRITICAL'))
);

CREATE TABLE auth_role (
    role_id       uuid         NOT NULL,
    tenant_id     varchar(64)  NOT NULL,
    role_code     varchar(120) NOT NULL,
    role_name     varchar(200) NOT NULL,
    role_status   varchar(24)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_auth_role PRIMARY KEY (role_id),
    CONSTRAINT uq_auth_role_code UNIQUE (tenant_id, role_code),
    CONSTRAINT ck_auth_role_status CHECK (role_status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE auth_role_capability (
    role_id          uuid         NOT NULL,
    capability_code  varchar(120) NOT NULL,
    granted_at       timestamptz  NOT NULL,
    CONSTRAINT pk_auth_role_capability PRIMARY KEY (role_id, capability_code),
    CONSTRAINT fk_auth_role_capability_role FOREIGN KEY (role_id) REFERENCES auth_role (role_id),
    CONSTRAINT fk_auth_role_capability_capability
        FOREIGN KEY (capability_code) REFERENCES auth_capability (capability_code)
);

CREATE TABLE auth_role_grant (
    grant_id       uuid         NOT NULL,
    tenant_id      varchar(64)  NOT NULL,
    principal_id   varchar(128) NOT NULL,
    role_id        uuid         NOT NULL,
    scope_type     varchar(32)  NOT NULL,
    scope_ref      varchar(128) NOT NULL,
    valid_from     timestamptz  NOT NULL,
    valid_to       timestamptz,
    source_code    varchar(40)  NOT NULL,
    approval_ref   varchar(160),
    revoked_at     timestamptz,
    revoked_by     varchar(128),
    revoke_reason  varchar(500),
    created_at     timestamptz  NOT NULL,
    CONSTRAINT pk_auth_role_grant PRIMARY KEY (grant_id),
    CONSTRAINT fk_auth_role_grant_role FOREIGN KEY (role_id) REFERENCES auth_role (role_id),
    CONSTRAINT ck_auth_role_grant_scope CHECK (scope_type IN ('TENANT', 'PROJECT', 'REGION', 'NETWORK')),
    CONSTRAINT ck_auth_role_grant_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_auth_role_grant_revoke CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL)
    )
);

CREATE INDEX ix_auth_grant_principal_effective
    ON auth_role_grant (tenant_id, principal_id, valid_from, valid_to)
    WHERE revoked_at IS NULL;

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('project.create', '创建运营项目', 'HIGH', now());
