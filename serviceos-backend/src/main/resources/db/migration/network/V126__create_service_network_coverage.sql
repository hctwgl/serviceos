-- M337：网点 ServiceCoverage（品牌/业务/行政区）作为 DISPATCH 地图 scope 权威。

CREATE TABLE net_service_network_coverage (
    coverage_id        uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    service_network_id uuid         NOT NULL,
    brand_code         varchar(64)  NOT NULL,
    business_type      varchar(96)  NOT NULL,
    region_code        varchar(16)  NOT NULL,
    coverage_status    varchar(24)  NOT NULL,
    valid_from         timestamptz  NOT NULL,
    valid_to           timestamptz,
    created_at         timestamptz  NOT NULL,
    CONSTRAINT pk_net_service_network_coverage PRIMARY KEY (coverage_id),
    CONSTRAINT uq_net_service_network_coverage_natural UNIQUE (
        tenant_id, service_network_id, brand_code, business_type, region_code, valid_from
    ),
    CONSTRAINT fk_net_service_network_coverage_network
        FOREIGN KEY (tenant_id, service_network_id)
        REFERENCES net_service_network (tenant_id, service_network_id),
    CONSTRAINT ck_net_service_network_coverage_status CHECK (
        coverage_status IN ('ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_net_service_network_coverage_window CHECK (
        valid_to IS NULL OR valid_to > valid_from
    )
);

CREATE INDEX ix_net_service_network_coverage_lookup
    ON net_service_network_coverage (
        tenant_id, brand_code, business_type, coverage_status, valid_from, valid_to
    );

CREATE INDEX ix_net_service_network_coverage_network
    ON net_service_network_coverage (tenant_id, service_network_id, coverage_status);
