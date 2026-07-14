-- M29：异常工作台首次提供可并发控制的人工确认动作和稳定列表游标。
ALTER TABLE ops_operational_exception
    DROP CONSTRAINT ck_ops_exception_status,
    DROP CONSTRAINT ck_ops_exception_resolution_evidence,
    ADD COLUMN acknowledged_at timestamptz,
    ADD COLUMN acknowledged_by varchar(128),
    ADD COLUMN acknowledgement_note varchar(500),
    ADD COLUMN aggregate_version bigint NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_ops_exception_version CHECK (aggregate_version >= 1),
    ADD CONSTRAINT ck_ops_exception_status
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    ADD CONSTRAINT ck_ops_exception_lifecycle_evidence CHECK (
        (status = 'OPEN'
            AND acknowledged_at IS NULL AND acknowledged_by IS NULL AND acknowledgement_note IS NULL
            AND resolved_at IS NULL AND resolution_code IS NULL
            AND resolution_action_ref IS NULL AND resolution_event_id IS NULL)
        OR
        (status = 'ACKNOWLEDGED'
            AND acknowledged_at IS NOT NULL AND acknowledged_by IS NOT NULL
            AND resolved_at IS NULL AND resolution_code IS NULL
            AND resolution_action_ref IS NULL AND resolution_event_id IS NULL)
        OR
        (status = 'RESOLVED'
            AND ((acknowledged_at IS NULL AND acknowledged_by IS NULL AND acknowledgement_note IS NULL)
                 OR (acknowledged_at IS NOT NULL AND acknowledged_by IS NOT NULL))
            AND resolved_at IS NOT NULL AND resolution_code IS NOT NULL
            AND resolution_action_ref IS NOT NULL AND resolution_event_id IS NOT NULL)
    );

DROP INDEX ix_ops_exception_open;
CREATE INDEX ix_ops_exception_workbench
    ON ops_operational_exception (tenant_id, opened_at DESC, exception_id DESC);
CREATE INDEX ix_ops_exception_active
    ON ops_operational_exception (tenant_id, severity_code, opened_at DESC, exception_id DESC)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED');

CREATE TABLE ops_exception_ack_result (
    tenant_id           varchar(64)  NOT NULL,
    idempotency_key     varchar(160) NOT NULL,
    exception_id        uuid         NOT NULL,
    aggregate_version   bigint       NOT NULL,
    acknowledged_at     timestamptz  NOT NULL,
    acknowledged_by     varchar(128) NOT NULL,
    CONSTRAINT pk_ops_exception_ack_result PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT fk_ops_exception_ack_result_exception
        FOREIGN KEY (exception_id) REFERENCES ops_operational_exception (exception_id)
);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('operations.exception.read', '查看运营异常', 'NORMAL', now()),
       ('operations.exception.acknowledge', '确认运营异常', 'HIGH', now());
