CREATE TABLE auth_field_policy (
    policy_id       uuid         NOT NULL,
    tenant_id       varchar(64)  NOT NULL,
    policy_code     varchar(120) NOT NULL,
    resource_type   varchar(120) NOT NULL,
    policy_version  integer      NOT NULL,
    policy_status   varchar(24)  NOT NULL,
    content_digest  char(64)     NOT NULL,
    published_at    timestamptz,
    created_at      timestamptz  NOT NULL,
    CONSTRAINT pk_auth_field_policy PRIMARY KEY (policy_id),
    CONSTRAINT uq_auth_field_policy_version UNIQUE (tenant_id, policy_code, policy_version),
    CONSTRAINT ck_auth_field_policy_version CHECK (policy_version > 0),
    CONSTRAINT ck_auth_field_policy_status CHECK (policy_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_auth_field_policy_publish CHECK (
        (policy_status = 'DRAFT' AND published_at IS NULL)
        OR (policy_status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
    )
);

CREATE TABLE auth_field_policy_rule (
    policy_id       uuid         NOT NULL,
    capability_code varchar(120) NOT NULL,
    field_code      varchar(160) NOT NULL,
    access_level    varchar(24)  NOT NULL,
    mask_code       varchar(80),
    CONSTRAINT pk_auth_field_policy_rule
        PRIMARY KEY (policy_id, capability_code, field_code),
    CONSTRAINT fk_auth_field_policy_rule_policy
        FOREIGN KEY (policy_id) REFERENCES auth_field_policy (policy_id),
    CONSTRAINT fk_auth_field_policy_rule_capability
        FOREIGN KEY (capability_code) REFERENCES auth_capability (capability_code),
    CONSTRAINT ck_auth_field_policy_rule_access
        CHECK (access_level IN ('HIDDEN', 'MASKED', 'READ', 'WRITE', 'EXPORT')),
    CONSTRAINT ck_auth_field_policy_rule_mask CHECK (
        (access_level = 'MASKED' AND mask_code IS NOT NULL)
        OR (access_level <> 'MASKED' AND mask_code IS NULL)
    )
);

CREATE TABLE auth_role_field_policy (
    role_id    uuid        NOT NULL,
    policy_id  uuid        NOT NULL,
    assigned_at timestamptz NOT NULL,
    CONSTRAINT pk_auth_role_field_policy PRIMARY KEY (role_id, policy_id),
    CONSTRAINT fk_auth_role_field_policy_role FOREIGN KEY (role_id) REFERENCES auth_role (role_id),
    CONSTRAINT fk_auth_role_field_policy_policy FOREIGN KEY (policy_id) REFERENCES auth_field_policy (policy_id)
);

CREATE INDEX ix_auth_field_policy_lookup
    ON auth_field_policy (tenant_id, resource_type, policy_status);
