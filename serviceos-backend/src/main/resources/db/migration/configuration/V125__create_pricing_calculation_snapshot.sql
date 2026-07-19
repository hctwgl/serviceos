-- M327：履约事实快照 + PRICING CalculationSnapshot（SHADOW，不落账）。

CREATE TABLE cfg_fulfillment_fact (
    fact_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    fact_code varchar(120) NOT NULL,
    value_type varchar(32) NOT NULL,
    value_text varchar(500),
    status varchar(24) NOT NULL,
    content_digest char(64) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_cfg_fulfillment_fact_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES prj_project (tenant_id, project_id),
    CONSTRAINT uq_cfg_fulfillment_fact_event_code
        UNIQUE (tenant_id, source_event_id, fact_code),
    CONSTRAINT ck_cfg_fulfillment_fact_status CHECK (status = 'CONFIRMED'),
    CONSTRAINT ck_cfg_fulfillment_fact_value_type CHECK (
        value_type IN ('STRING', 'NUMBER', 'BOOLEAN')
    ),
    CONSTRAINT ck_cfg_fulfillment_fact_digest CHECK (content_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_cfg_fulfillment_fact_wo
    ON cfg_fulfillment_fact (tenant_id, work_order_id, created_at DESC);

CREATE TABLE cfg_calculation_snapshot (
    snapshot_id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    project_id uuid NOT NULL,
    work_order_id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    source_event_type varchar(80) NOT NULL,
    bundle_id uuid NOT NULL,
    bundle_digest char(64) NOT NULL,
    pricing_key varchar(120) NOT NULL,
    asset_version_id uuid NOT NULL,
    asset_content_digest char(64) NOT NULL,
    currency varchar(8) NOT NULL,
    total_amount_minor bigint NOT NULL,
    matched_lines_json jsonb NOT NULL,
    explanations_json jsonb NOT NULL,
    facts_digest char(64) NOT NULL,
    mode varchar(24) NOT NULL,
    correlation_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT fk_cfg_calculation_snapshot_project
        FOREIGN KEY (tenant_id, project_id) REFERENCES prj_project (tenant_id, project_id),
    CONSTRAINT fk_cfg_calculation_snapshot_bundle
        FOREIGN KEY (tenant_id, bundle_id) REFERENCES cfg_configuration_bundle (tenant_id, bundle_id),
    CONSTRAINT uq_cfg_calculation_snapshot_event_pricing
        UNIQUE (tenant_id, source_event_id, pricing_key),
    CONSTRAINT ck_cfg_calculation_snapshot_mode CHECK (mode = 'SHADOW'),
    CONSTRAINT ck_cfg_calculation_snapshot_amount CHECK (total_amount_minor >= 0),
    CONSTRAINT ck_cfg_calculation_snapshot_digests CHECK (
        bundle_digest ~ '^[0-9a-f]{64}$'
        AND asset_content_digest ~ '^[0-9a-f]{64}$'
        AND facts_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX ix_cfg_calculation_snapshot_wo
    ON cfg_calculation_snapshot (tenant_id, work_order_id, created_at DESC);

COMMENT ON TABLE cfg_fulfillment_fact IS
    'M327：从工单表达式上下文提取的最小 CONFIRMED 履约事实';
COMMENT ON TABLE cfg_calculation_snapshot IS
    'M327：PricingRuntime 试算 SHADOW 快照；不落账、不进结算';
