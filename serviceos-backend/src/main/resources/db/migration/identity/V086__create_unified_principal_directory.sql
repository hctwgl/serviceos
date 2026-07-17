CREATE TABLE idn_security_principal (
    principal_id       uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    principal_type     varchar(24)  NOT NULL,
    principal_status   varchar(24)  NOT NULL,
    aggregate_version  bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    disabled_at        timestamptz,
    disabled_by        varchar(128),
    disabled_reason    varchar(500),
    CONSTRAINT pk_idn_security_principal PRIMARY KEY (principal_id),
    CONSTRAINT uq_idn_security_principal_tenant UNIQUE (tenant_id, principal_id),
    CONSTRAINT ck_idn_security_principal_type
        CHECK (principal_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_idn_security_principal_status
        CHECK (principal_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_idn_security_principal_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_idn_security_principal_disabled CHECK (
        (principal_status = 'ACTIVE' AND disabled_at IS NULL
            AND disabled_by IS NULL AND disabled_reason IS NULL)
        OR
        (principal_status = 'DISABLED' AND disabled_at IS NOT NULL
            AND disabled_by IS NOT NULL AND disabled_reason IS NOT NULL)
    )
);

CREATE TABLE idn_identity_link (
    identity_link_id  uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    principal_id      uuid         NOT NULL,
    issuer            varchar(512) NOT NULL,
    subject_value     varchar(255) NOT NULL,
    client_id         varchar(128),
    linked_by         varchar(128) NOT NULL,
    linked_at         timestamptz  NOT NULL,
    CONSTRAINT pk_idn_identity_link PRIMARY KEY (identity_link_id),
    CONSTRAINT uq_idn_identity_link_subject UNIQUE (tenant_id, issuer, subject_value),
    CONSTRAINT fk_idn_identity_link_principal FOREIGN KEY (tenant_id, principal_id)
        REFERENCES idn_security_principal (tenant_id, principal_id),
    CONSTRAINT ck_idn_identity_link_issuer CHECK (length(btrim(issuer)) > 0),
    CONSTRAINT ck_idn_identity_link_subject CHECK (length(btrim(subject_value)) > 0)
);

CREATE INDEX ix_idn_identity_link_principal
    ON idn_identity_link (tenant_id, principal_id, linked_at, identity_link_id);

CREATE TABLE idn_person_profile (
    principal_id     uuid         NOT NULL,
    tenant_id        varchar(64)  NOT NULL,
    display_name     varchar(200) NOT NULL,
    employee_number  varchar(128),
    profile_version  bigint       NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    updated_by       varchar(128) NOT NULL,
    CONSTRAINT pk_idn_person_profile PRIMARY KEY (principal_id),
    CONSTRAINT fk_idn_person_profile_principal FOREIGN KEY (tenant_id, principal_id)
        REFERENCES idn_security_principal (tenant_id, principal_id),
    CONSTRAINT uq_idn_person_profile_employee UNIQUE (tenant_id, employee_number),
    CONSTRAINT ck_idn_person_profile_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_idn_person_profile_version CHECK (profile_version > 0)
);

CREATE INDEX ix_idn_person_profile_display_name
    ON idn_person_profile (tenant_id, lower(display_name), principal_id);

CREATE TABLE idn_principal_persona (
    persona_id       uuid        NOT NULL,
    tenant_id        varchar(64) NOT NULL,
    principal_id     uuid        NOT NULL,
    persona_type     varchar(40) NOT NULL,
    persona_status   varchar(24) NOT NULL,
    valid_from       timestamptz NOT NULL,
    valid_to         timestamptz,
    persona_version  bigint      NOT NULL,
    created_by       varchar(128) NOT NULL,
    created_at       timestamptz NOT NULL,
    CONSTRAINT pk_idn_principal_persona PRIMARY KEY (persona_id),
    CONSTRAINT fk_idn_principal_persona_principal FOREIGN KEY (tenant_id, principal_id)
        REFERENCES idn_security_principal (tenant_id, principal_id),
    CONSTRAINT uq_idn_principal_persona_type UNIQUE (tenant_id, principal_id, persona_type),
    CONSTRAINT ck_idn_principal_persona_type CHECK (
        persona_type IN ('INTERNAL_EMPLOYEE', 'NETWORK_MEMBER', 'TECHNICIAN', 'CONSUMER', 'SERVICE_ACCOUNT')
    ),
    CONSTRAINT ck_idn_principal_persona_status CHECK (persona_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_idn_principal_persona_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_idn_principal_persona_version CHECK (persona_version > 0)
);

CREATE INDEX ix_idn_principal_persona_effective
    ON idn_principal_persona (tenant_id, principal_id, valid_from, valid_to)
    WHERE persona_status = 'ACTIVE';

CREATE TABLE idn_principal_lifecycle_event (
    lifecycle_event_id  uuid         NOT NULL,
    tenant_id           varchar(64)  NOT NULL,
    principal_id        uuid         NOT NULL,
    event_type          varchar(40)  NOT NULL,
    principal_version   bigint       NOT NULL,
    reason              varchar(500),
    actor_id            varchar(128) NOT NULL,
    request_digest      char(64)     NOT NULL,
    correlation_id      varchar(128) NOT NULL,
    occurred_at         timestamptz  NOT NULL,
    CONSTRAINT pk_idn_principal_lifecycle_event PRIMARY KEY (lifecycle_event_id),
    CONSTRAINT fk_idn_principal_lifecycle_event_principal FOREIGN KEY (tenant_id, principal_id)
        REFERENCES idn_security_principal (tenant_id, principal_id),
    CONSTRAINT ck_idn_principal_lifecycle_event_type CHECK (
        event_type IN ('REGISTERED', 'IDENTITY_LINKED', 'PROFILE_UPDATED',
                       'PERSONA_ADDED', 'DISABLED', 'ENABLED')
    ),
    CONSTRAINT ck_idn_principal_lifecycle_event_version CHECK (principal_version > 0),
    CONSTRAINT ck_idn_principal_lifecycle_event_digest CHECK (request_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_idn_principal_lifecycle_event_history
    ON idn_principal_lifecycle_event (tenant_id, principal_id, occurred_at, lifecycle_event_id);

CREATE OR REPLACE FUNCTION idn_reject_immutable_fact_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'identity directory immutable fact cannot be changed';
END;
$$;

CREATE TRIGGER trg_idn_identity_link_immutable
    BEFORE UPDATE OR DELETE ON idn_identity_link
    FOR EACH ROW EXECUTE FUNCTION idn_reject_immutable_fact_mutation();

CREATE TRIGGER trg_idn_lifecycle_event_immutable
    BEFORE UPDATE OR DELETE ON idn_principal_lifecycle_event
    FOR EACH ROW EXECUTE FUNCTION idn_reject_immutable_fact_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('identity.read', '读取统一主体目录', 'HIGH', now()),
    ('identity.readSensitive', '读取主体身份绑定', 'CRITICAL', now()),
    ('identity.manageLinks', '管理主体身份绑定', 'CRITICAL', now()),
    ('identity.manageLifecycle', '启停统一主体', 'CRITICAL', now()),
    ('identity.manageProfile', '维护主体档案与 Persona', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;
