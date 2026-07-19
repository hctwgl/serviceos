-- M326：NOTIFICATION 可靠投递闭环——Intent / Delivery / Attempt。
-- LocalReference SENT 即本地 ACK；UNKNOWN/FAILED 保留人工接管标记，不伪造成功。

CREATE TABLE cfg_notification_intent (
    intent_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    source_event_type varchar(80) NOT NULL,
    source_aggregate_type varchar(80) NOT NULL,
    source_aggregate_id varchar(120) NOT NULL,
    work_order_id uuid,
    task_id uuid,
    bundle_id uuid NOT NULL,
    bundle_digest char(64) NOT NULL,
    policy_key varchar(120) NOT NULL,
    asset_version_id uuid NOT NULL,
    content_digest char(64) NOT NULL,
    status varchar(24) NOT NULL,
    requires_manual_intervention boolean NOT NULL,
    explanation_digest char(64) NOT NULL,
    correlation_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    resolved_at timestamptz NOT NULL,
    CONSTRAINT fk_cfg_notification_intent_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES prj_project (tenant_id, project_id),
    CONSTRAINT fk_cfg_notification_intent_bundle
        FOREIGN KEY (tenant_id, bundle_id) REFERENCES cfg_configuration_bundle (tenant_id, bundle_id),
    CONSTRAINT uq_cfg_notification_intent_event_policy
        UNIQUE (tenant_id, source_event_id, policy_key),
    CONSTRAINT ck_cfg_notification_intent_digest CHECK (
        bundle_digest ~ '^[0-9a-f]{64}$'
        AND content_digest ~ '^[0-9a-f]{64}$'
        AND explanation_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_cfg_notification_intent_status CHECK (
        status IN ('COMPLETED', 'PARTIAL', 'FAILED')
    )
);

CREATE INDEX ix_cfg_notification_intent_task
    ON cfg_notification_intent (tenant_id, task_id, created_at DESC)
    WHERE task_id IS NOT NULL;

CREATE INDEX ix_cfg_notification_intent_manual
    ON cfg_notification_intent (tenant_id, project_id, created_at DESC)
    WHERE requires_manual_intervention;

CREATE TABLE cfg_notification_delivery (
    delivery_id uuid PRIMARY KEY,
    intent_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    trigger_key varchar(120) NOT NULL,
    event_type varchar(80) NOT NULL,
    channel varchar(40) NOT NULL,
    recipient_principal_id varchar(160) NOT NULL,
    template_key varchar(120) NOT NULL,
    idempotency_key char(64) NOT NULL,
    status varchar(24) NOT NULL,
    provider_detail varchar(240),
    created_at timestamptz NOT NULL,
    acknowledged_at timestamptz,
    CONSTRAINT fk_cfg_notification_delivery_intent
        FOREIGN KEY (intent_id) REFERENCES cfg_notification_intent (intent_id),
    CONSTRAINT uq_cfg_notification_delivery_idempotency
        UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_cfg_notification_delivery_idempotency CHECK (
        idempotency_key ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_cfg_notification_delivery_status CHECK (
        status IN ('SENT', 'SENT_REPLAY', 'UNKNOWN', 'FAILED')
    ),
    CONSTRAINT ck_cfg_notification_delivery_ack CHECK (
        (status IN ('SENT', 'SENT_REPLAY') AND acknowledged_at IS NOT NULL)
        OR
        (status IN ('UNKNOWN', 'FAILED') AND acknowledged_at IS NULL)
    )
);

CREATE INDEX ix_cfg_notification_delivery_intent
    ON cfg_notification_delivery (tenant_id, intent_id, created_at);

CREATE TABLE cfg_notification_attempt (
    attempt_id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    attempt_no integer NOT NULL,
    outcome varchar(24) NOT NULL,
    detail varchar(240),
    started_at timestamptz NOT NULL,
    finished_at timestamptz NOT NULL,
    CONSTRAINT fk_cfg_notification_attempt_delivery
        FOREIGN KEY (delivery_id) REFERENCES cfg_notification_delivery (delivery_id),
    CONSTRAINT uq_cfg_notification_attempt_no
        UNIQUE (delivery_id, attempt_no),
    CONSTRAINT ck_cfg_notification_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_cfg_notification_attempt_outcome CHECK (
        outcome IN ('SENT', 'SENT_REPLAY', 'UNKNOWN', 'FAILED')
    )
);

CREATE INDEX ix_cfg_notification_attempt_delivery
    ON cfg_notification_attempt (tenant_id, delivery_id, attempt_no);

COMMENT ON TABLE cfg_notification_intent IS
    'M326：一次领域事件 × 一个冻结 NOTIFICATION policy 的投递意图';
COMMENT ON TABLE cfg_notification_delivery IS
    'M326：某收件人某渠道的交付事实；SENT/SENT_REPLAY 视为本地 ACK';
COMMENT ON TABLE cfg_notification_attempt IS
    'M326：供应商/通道技术尝试；业务重试时钟不在本表';
