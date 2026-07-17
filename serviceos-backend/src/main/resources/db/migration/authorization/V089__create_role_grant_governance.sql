-- M186：Role/Capability 治理、RoleGrant 申请审批撤销、Delegation、grant generation。
-- auth_* 不对 org_/idn_/net_ 建立物理外键。

ALTER TABLE auth_role
    ADD COLUMN role_kind varchar(24),
    ADD COLUMN description varchar(500),
    ADD COLUMN aggregate_version bigint,
    ADD COLUMN updated_at timestamptz;

UPDATE auth_role
   SET role_kind = 'TENANT',
       aggregate_version = 1,
       updated_at = created_at
 WHERE role_kind IS NULL;

ALTER TABLE auth_role
    ALTER COLUMN role_kind SET NOT NULL,
    ALTER COLUMN role_kind SET DEFAULT 'TENANT',
    ALTER COLUMN aggregate_version SET NOT NULL,
    ALTER COLUMN aggregate_version SET DEFAULT 1,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE auth_role
    ADD CONSTRAINT ck_auth_role_kind CHECK (role_kind IN ('PLATFORM_TEMPLATE', 'TENANT')),
    ADD CONSTRAINT ck_auth_role_version CHECK (aggregate_version > 0);

ALTER TABLE auth_role_grant
    ADD COLUMN grant_status varchar(32),
    ADD COLUMN grant_effect varchar(16),
    ADD COLUMN requested_by varchar(128),
    ADD COLUMN request_reason varchar(500),
    ADD COLUMN approved_by varchar(128),
    ADD COLUMN approved_at timestamptz,
    ADD COLUMN rejected_by varchar(128),
    ADD COLUMN rejected_at timestamptz,
    ADD COLUMN reject_reason varchar(500),
    ADD COLUMN aggregate_version bigint,
    ADD COLUMN updated_at timestamptz;

UPDATE auth_role_grant
   SET grant_status = CASE WHEN revoked_at IS NULL THEN 'ACTIVE' ELSE 'REVOKED' END,
       grant_effect = 'ALLOW',
       aggregate_version = 1,
       updated_at = created_at
 WHERE grant_status IS NULL;

ALTER TABLE auth_role_grant
    ALTER COLUMN grant_status SET NOT NULL,
    ALTER COLUMN grant_status SET DEFAULT 'ACTIVE',
    ALTER COLUMN grant_effect SET NOT NULL,
    ALTER COLUMN grant_effect SET DEFAULT 'ALLOW',
    ALTER COLUMN aggregate_version SET NOT NULL,
    ALTER COLUMN aggregate_version SET DEFAULT 1,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE auth_role_grant
    ADD CONSTRAINT ck_auth_role_grant_status CHECK (
        grant_status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'REVOKED')
    ),
    ADD CONSTRAINT ck_auth_role_grant_effect CHECK (grant_effect IN ('ALLOW', 'DENY')),
    ADD CONSTRAINT ck_auth_role_grant_version CHECK (aggregate_version > 0),
    ADD CONSTRAINT ck_auth_role_grant_decision CHECK (
        (grant_status <> 'REJECTED'
            AND rejected_at IS NULL AND rejected_by IS NULL AND reject_reason IS NULL)
        OR (grant_status = 'REJECTED'
            AND rejected_at IS NOT NULL AND rejected_by IS NOT NULL AND reject_reason IS NOT NULL)
    );

DROP INDEX IF EXISTS ix_auth_grant_principal_effective;
CREATE INDEX ix_auth_grant_principal_effective
    ON auth_role_grant (tenant_id, principal_id, valid_from, valid_to)
    WHERE grant_status = 'ACTIVE' AND revoked_at IS NULL;

CREATE TABLE auth_role_grant_event (
    event_id          uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    event_type        varchar(64)  NOT NULL,
    resource_type     varchar(64)  NOT NULL,
    resource_id       uuid         NOT NULL,
    resource_version  bigint       NOT NULL,
    reason            varchar(500),
    actor_id          varchar(128) NOT NULL,
    request_digest    varchar(64)  NOT NULL,
    correlation_id    varchar(128) NOT NULL,
    occurred_at       timestamptz  NOT NULL,
    CONSTRAINT pk_auth_role_grant_event PRIMARY KEY (event_id),
    CONSTRAINT ck_auth_role_grant_event_version CHECK (resource_version > 0)
);

CREATE INDEX ix_auth_role_grant_event_resource
    ON auth_role_grant_event (tenant_id, resource_type, resource_id, occurred_at);

CREATE OR REPLACE FUNCTION auth_reject_immutable_fact_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'auth immutable fact mutation is forbidden: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_auth_role_grant_event_immutable
    BEFORE UPDATE OR DELETE ON auth_role_grant_event
    FOR EACH ROW EXECUTE FUNCTION auth_reject_immutable_fact_mutation();

CREATE TABLE auth_delegation (
    delegation_id           uuid         NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    delegator_principal_id  varchar(128) NOT NULL,
    delegate_principal_id   varchar(128) NOT NULL,
    scope_type              varchar(32)  NOT NULL,
    scope_ref               varchar(128) NOT NULL,
    valid_from              timestamptz  NOT NULL,
    valid_to                timestamptz,
    reason                  varchar(500) NOT NULL,
    delegation_status       varchar(24)  NOT NULL,
    created_by              varchar(128) NOT NULL,
    created_at              timestamptz  NOT NULL,
    revoked_by              varchar(128),
    revoked_at              timestamptz,
    revoke_reason           varchar(500),
    aggregate_version       bigint       NOT NULL,
    updated_at              timestamptz  NOT NULL,
    CONSTRAINT pk_auth_delegation PRIMARY KEY (delegation_id),
    CONSTRAINT ck_auth_delegation_scope CHECK (scope_type IN ('TENANT', 'PROJECT', 'REGION', 'NETWORK')),
    CONSTRAINT ck_auth_delegation_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_auth_delegation_status CHECK (delegation_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_auth_delegation_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_auth_delegation_revoke CHECK (
        (delegation_status = 'ACTIVE'
            AND revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
        OR (delegation_status = 'REVOKED'
            AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL)
    )
);

CREATE INDEX ix_auth_delegation_delegate_effective
    ON auth_delegation (tenant_id, delegate_principal_id, valid_from, valid_to)
    WHERE delegation_status = 'ACTIVE' AND revoked_at IS NULL;

CREATE TABLE auth_delegation_capability (
    delegation_id    uuid         NOT NULL,
    capability_code  varchar(120) NOT NULL,
    CONSTRAINT pk_auth_delegation_capability PRIMARY KEY (delegation_id, capability_code),
    CONSTRAINT fk_auth_delegation_capability_delegation
        FOREIGN KEY (delegation_id) REFERENCES auth_delegation (delegation_id),
    CONSTRAINT fk_auth_delegation_capability_capability
        FOREIGN KEY (capability_code) REFERENCES auth_capability (capability_code)
);

CREATE TABLE auth_tenant_grant_generation (
    tenant_id   varchar(64) NOT NULL,
    generation  bigint      NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT pk_auth_tenant_grant_generation PRIMARY KEY (tenant_id),
    CONSTRAINT ck_auth_tenant_grant_generation CHECK (generation > 0)
);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('authorization.read', '读取角色与授权治理目录', 'HIGH', now()),
    ('authorization.manageRoles', '管理租户角色与能力组合', 'CRITICAL', now()),
    ('authorization.requestGrant', '申请 RoleGrant', 'HIGH', now()),
    ('authorization.approveGrant', '批准或拒绝 RoleGrant', 'CRITICAL', now()),
    ('authorization.revokeGrant', '撤销 RoleGrant', 'CRITICAL', now()),
    ('authorization.delegate', '创建或撤销 Delegation', 'HIGH', now()),
    ('authorization.explain', '解释授权判定', 'CRITICAL', now())
ON CONFLICT (capability_code) DO NOTHING;
