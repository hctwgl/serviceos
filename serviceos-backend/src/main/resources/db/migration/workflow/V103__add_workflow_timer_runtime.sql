-- M276：TIMER 中间捕获事件。到期由 worker claim/lease 唤醒，禁止无锁扫表双触发。

CREATE TABLE wfl_timer_subscription (
    timer_subscription_id      uuid         NOT NULL,
    tenant_id                  varchar(64)  NOT NULL,
    project_id                 uuid         NOT NULL,
    workflow_instance_id       uuid         NOT NULL,
    workflow_node_instance_id  uuid         NOT NULL,
    work_order_id              uuid         NOT NULL,
    node_id                    varchar(100) NOT NULL,
    duration_seconds           int          NOT NULL,
    fire_at                    timestamptz  NOT NULL,
    status                     varchar(24)  NOT NULL,
    claim_owner                varchar(128),
    claim_until                timestamptz,
    activation_event_id        uuid         NOT NULL,
    fire_event_id              uuid,
    version                    bigint       NOT NULL DEFAULT 1,
    activated_at               timestamptz  NOT NULL,
    fired_at                   timestamptz,
    CONSTRAINT pk_wfl_timer_subscription PRIMARY KEY (timer_subscription_id),
    CONSTRAINT fk_wfl_timer_workflow FOREIGN KEY (tenant_id, workflow_instance_id)
        REFERENCES wfl_workflow_instance (tenant_id, workflow_instance_id),
    CONSTRAINT fk_wfl_timer_node FOREIGN KEY (workflow_node_instance_id)
        REFERENCES wfl_node_instance (workflow_node_instance_id),
    CONSTRAINT ck_wfl_timer_status CHECK (status IN ('WAITING', 'CLAIMED', 'FIRED', 'CANCELLED')),
    CONSTRAINT ck_wfl_timer_duration CHECK (duration_seconds >= 1),
    CONSTRAINT ck_wfl_timer_claim CHECK (
        (status = 'CLAIMED' AND claim_owner IS NOT NULL AND claim_until IS NOT NULL)
        OR (status <> 'CLAIMED' AND claim_owner IS NULL AND claim_until IS NULL)
    ),
    CONSTRAINT ck_wfl_timer_fire CHECK (
        (status = 'FIRED' AND fire_event_id IS NOT NULL AND fired_at IS NOT NULL)
        OR (status <> 'FIRED' AND fire_event_id IS NULL AND fired_at IS NULL)
    ),
    CONSTRAINT ck_wfl_timer_version CHECK (version >= 1)
);

CREATE INDEX ix_wfl_timer_due
    ON wfl_timer_subscription (status, fire_at, timer_subscription_id)
    WHERE status IN ('WAITING', 'CLAIMED');

CREATE UNIQUE INDEX uq_wfl_timer_node_waiting
    ON wfl_timer_subscription (tenant_id, workflow_node_instance_id)
    WHERE status IN ('WAITING', 'CLAIMED');
