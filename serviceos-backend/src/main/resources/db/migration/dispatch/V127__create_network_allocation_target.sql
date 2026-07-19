-- M338：网点月度签约比例目标（committedShare），供 DISPATCH ALLOCATION_RATIO_GAP 评分。

CREATE TABLE dsp_network_allocation_target (
    target_id        uuid           NOT NULL,
    tenant_id        varchar(64)    NOT NULL,
    project_id       uuid           NOT NULL,
    network_id       varchar(128)   NOT NULL,
    brand_code       varchar(64)    NOT NULL,
    business_type    varchar(96)    NOT NULL,
    committed_share  numeric(9, 6)  NOT NULL,
    valid_from       timestamptz    NOT NULL,
    valid_to         timestamptz,
    created_at       timestamptz    NOT NULL,
    CONSTRAINT pk_dsp_network_allocation_target PRIMARY KEY (target_id),
    CONSTRAINT uq_dsp_network_allocation_target_natural UNIQUE (
        tenant_id, project_id, network_id, brand_code, business_type, valid_from
    ),
    CONSTRAINT ck_dsp_network_allocation_target_share CHECK (
        committed_share >= 0 AND committed_share <= 1
    ),
    CONSTRAINT ck_dsp_network_allocation_target_network CHECK (
        network_id = btrim(network_id) AND network_id <> ''
    ),
    CONSTRAINT ck_dsp_network_allocation_target_window CHECK (
        valid_to IS NULL OR valid_to > valid_from
    )
);

CREATE INDEX ix_dsp_network_allocation_target_lookup
    ON dsp_network_allocation_target (
        tenant_id, project_id, brand_code, business_type, valid_from, valid_to
    );
