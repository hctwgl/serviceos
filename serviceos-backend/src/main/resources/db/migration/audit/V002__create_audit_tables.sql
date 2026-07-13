CREATE TABLE aud_audit_record (
    audit_id        uuid         NOT NULL,
    tenant_id       varchar(64)  NOT NULL,
    actor_id        varchar(128) NOT NULL,
    action_name     varchar(100) NOT NULL,
    target_type     varchar(100) NOT NULL,
    target_id       varchar(128) NOT NULL,
    result_code     varchar(40)  NOT NULL,
    request_digest  char(64)     NOT NULL,
    correlation_id  varchar(128) NOT NULL,
    occurred_at     timestamptz  NOT NULL,
    CONSTRAINT pk_aud_audit_record PRIMARY KEY (audit_id)
);

CREATE INDEX ix_aud_audit_target
    ON aud_audit_record (tenant_id, target_type, target_id, occurred_at DESC);

CREATE INDEX ix_aud_audit_correlation
    ON aud_audit_record (tenant_id, correlation_id);
