-- M184：企业组织、OrgUnit closure、任职历史、同步收据与待重分配清单。

CREATE TABLE org_organization (
    organization_id     uuid         NOT NULL,
    tenant_id           varchar(64)  NOT NULL,
    organization_code   varchar(64)  NOT NULL,
    organization_name   varchar(200) NOT NULL,
    authority_mode      varchar(32)  NOT NULL,
    organization_status varchar(24)  NOT NULL,
    source_system       varchar(64),
    source_key          varchar(128),
    aggregate_version   bigint       NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT pk_org_organization PRIMARY KEY (organization_id),
    CONSTRAINT uq_org_organization_tenant UNIQUE (tenant_id, organization_id),
    CONSTRAINT uq_org_organization_code UNIQUE (tenant_id, organization_code),
    CONSTRAINT uq_org_organization_source UNIQUE (tenant_id, source_system, source_key),
    CONSTRAINT ck_org_organization_authority CHECK (
        authority_mode IN ('LOCAL', 'EXTERNAL_AUTHORITATIVE')
    ),
    CONSTRAINT ck_org_organization_status CHECK (
        organization_status IN ('ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_org_organization_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_org_organization_source CHECK (
        (authority_mode = 'LOCAL' AND source_system IS NULL AND source_key IS NULL)
        OR
        (authority_mode = 'EXTERNAL_AUTHORITATIVE'
            AND source_system IS NOT NULL AND length(btrim(source_system)) > 0
            AND source_key IS NOT NULL AND length(btrim(source_key)) > 0)
    )
);

CREATE TABLE org_unit (
    org_unit_id       uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    organization_id   uuid         NOT NULL,
    parent_unit_id    uuid,
    unit_code         varchar(64)  NOT NULL,
    unit_name         varchar(200) NOT NULL,
    unit_status       varchar(24)  NOT NULL,
    source_system     varchar(64),
    source_key        varchar(128),
    source_version    bigint,
    aggregate_version bigint       NOT NULL,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT pk_org_unit PRIMARY KEY (org_unit_id),
    CONSTRAINT uq_org_unit_tenant UNIQUE (tenant_id, org_unit_id),
    CONSTRAINT uq_org_unit_code UNIQUE (tenant_id, organization_id, unit_code),
    CONSTRAINT uq_org_unit_source UNIQUE (tenant_id, source_system, source_key),
    CONSTRAINT fk_org_unit_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES org_organization (tenant_id, organization_id),
    CONSTRAINT fk_org_unit_parent FOREIGN KEY (tenant_id, parent_unit_id)
        REFERENCES org_unit (tenant_id, org_unit_id),
    CONSTRAINT ck_org_unit_status CHECK (unit_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_org_unit_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_org_unit_not_self CHECK (parent_unit_id IS NULL OR parent_unit_id <> org_unit_id),
    CONSTRAINT ck_org_unit_source_version CHECK (source_version IS NULL OR source_version > 0)
);

CREATE INDEX ix_org_unit_org_parent
    ON org_unit (tenant_id, organization_id, parent_unit_id, unit_code);

CREATE TABLE org_unit_closure (
    tenant_id       varchar(64) NOT NULL,
    organization_id uuid        NOT NULL,
    ancestor_id     uuid        NOT NULL,
    descendant_id   uuid        NOT NULL,
    depth           integer     NOT NULL,
    CONSTRAINT pk_org_unit_closure PRIMARY KEY (tenant_id, ancestor_id, descendant_id),
    CONSTRAINT fk_org_unit_closure_ancestor FOREIGN KEY (tenant_id, ancestor_id)
        REFERENCES org_unit (tenant_id, org_unit_id),
    CONSTRAINT fk_org_unit_closure_descendant FOREIGN KEY (tenant_id, descendant_id)
        REFERENCES org_unit (tenant_id, org_unit_id),
    CONSTRAINT fk_org_unit_closure_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES org_organization (tenant_id, organization_id),
    CONSTRAINT ck_org_unit_closure_depth CHECK (depth >= 0),
    CONSTRAINT ck_org_unit_closure_self CHECK (
        (ancestor_id = descendant_id AND depth = 0)
        OR (ancestor_id <> descendant_id AND depth > 0)
    )
);

CREATE INDEX ix_org_unit_closure_descendant
    ON org_unit_closure (tenant_id, organization_id, descendant_id, depth);

CREATE INDEX ix_org_unit_closure_ancestor
    ON org_unit_closure (tenant_id, organization_id, ancestor_id, depth);

CREATE TABLE org_membership (
    membership_id     uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    organization_id   uuid         NOT NULL,
    org_unit_id       uuid         NOT NULL,
    principal_id      uuid         NOT NULL,
    membership_type   varchar(24)  NOT NULL,
    membership_status varchar(24)  NOT NULL,
    valid_from        timestamptz  NOT NULL,
    valid_to          timestamptz,
    source_system     varchar(64),
    source_key        varchar(128),
    source_version    bigint,
    created_by        varchar(128) NOT NULL,
    created_at        timestamptz  NOT NULL,
    terminated_by     varchar(128),
    terminated_at     timestamptz,
    terminate_reason  varchar(500),
    aggregate_version bigint       NOT NULL,
    CONSTRAINT pk_org_membership PRIMARY KEY (membership_id),
    CONSTRAINT uq_org_membership_tenant UNIQUE (tenant_id, membership_id),
    CONSTRAINT uq_org_membership_source UNIQUE (tenant_id, source_system, source_key),
    CONSTRAINT fk_org_membership_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES org_organization (tenant_id, organization_id),
    CONSTRAINT fk_org_membership_unit FOREIGN KEY (tenant_id, org_unit_id)
        REFERENCES org_unit (tenant_id, org_unit_id),
    CONSTRAINT ck_org_membership_type CHECK (
        membership_type IN ('PRIMARY', 'SECONDARY', 'MANAGER')
    ),
    CONSTRAINT ck_org_membership_status CHECK (
        membership_status IN ('ACTIVE', 'TERMINATED')
    ),
    CONSTRAINT ck_org_membership_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_org_membership_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_org_membership_source_version CHECK (source_version IS NULL OR source_version > 0),
    CONSTRAINT ck_org_membership_terminate CHECK (
        (membership_status = 'ACTIVE'
            AND valid_to IS NULL
            AND terminated_by IS NULL
            AND terminated_at IS NULL
            AND terminate_reason IS NULL)
        OR
        (membership_status = 'TERMINATED'
            AND valid_to IS NOT NULL
            AND terminated_by IS NOT NULL
            AND terminated_at IS NOT NULL
            AND terminate_reason IS NOT NULL)
    )
);

CREATE INDEX ix_org_membership_principal
    ON org_membership (tenant_id, principal_id, valid_from, valid_to);

CREATE INDEX ix_org_membership_unit
    ON org_membership (tenant_id, org_unit_id, membership_status, membership_type);

-- 同一主体同一时刻最多一条有效主职。
CREATE UNIQUE INDEX uq_org_membership_active_primary
    ON org_membership (tenant_id, principal_id)
    WHERE membership_type = 'PRIMARY' AND membership_status = 'ACTIVE';

CREATE TABLE org_directory_sync_batch (
    batch_id           uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    organization_id    uuid         NOT NULL,
    source_system      varchar(64)  NOT NULL,
    external_batch_key varchar(128) NOT NULL,
    batch_status       varchar(24)  NOT NULL,
    received_at        timestamptz  NOT NULL,
    completed_at       timestamptz,
    actor_id           varchar(128) NOT NULL,
    correlation_id     varchar(128) NOT NULL,
    request_digest     char(64)     NOT NULL,
    success_count      integer      NOT NULL DEFAULT 0,
    failed_count       integer      NOT NULL DEFAULT 0,
    skipped_count      integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_org_directory_sync_batch PRIMARY KEY (batch_id),
    CONSTRAINT uq_org_directory_sync_batch UNIQUE (tenant_id, source_system, external_batch_key),
    CONSTRAINT fk_org_directory_sync_batch_org FOREIGN KEY (tenant_id, organization_id)
        REFERENCES org_organization (tenant_id, organization_id),
    CONSTRAINT ck_org_directory_sync_batch_status CHECK (
        batch_status IN ('RECEIVED', 'COMPLETED', 'COMPLETED_WITH_ERRORS')
    ),
    CONSTRAINT ck_org_directory_sync_batch_digest CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_org_directory_sync_batch_counts CHECK (
        success_count >= 0 AND failed_count >= 0 AND skipped_count >= 0
    )
);

CREATE TABLE org_directory_sync_item (
    item_id           uuid         NOT NULL,
    batch_id          uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    item_index        integer      NOT NULL,
    operation_type    varchar(40)  NOT NULL,
    source_key        varchar(128) NOT NULL,
    external_version  bigint       NOT NULL,
    item_status       varchar(24)  NOT NULL,
    result_code       varchar(64),
    result_message    varchar(500),
    resource_type     varchar(40),
    resource_id       uuid,
    processed_at      timestamptz  NOT NULL,
    CONSTRAINT pk_org_directory_sync_item PRIMARY KEY (item_id),
    CONSTRAINT uq_org_directory_sync_item UNIQUE (batch_id, item_index),
    CONSTRAINT fk_org_directory_sync_item_batch FOREIGN KEY (batch_id)
        REFERENCES org_directory_sync_batch (batch_id),
    CONSTRAINT ck_org_directory_sync_item_operation CHECK (
        operation_type IN ('UPSERT_UNIT', 'UPSERT_MEMBERSHIP', 'TERMINATE_MEMBERSHIP')
    ),
    CONSTRAINT ck_org_directory_sync_item_status CHECK (
        item_status IN ('SUCCESS', 'FAILED', 'SKIPPED')
    ),
    CONSTRAINT ck_org_directory_sync_item_index CHECK (item_index >= 0),
    CONSTRAINT ck_org_directory_sync_item_version CHECK (external_version > 0)
);

CREATE INDEX ix_org_directory_sync_item_batch
    ON org_directory_sync_item (tenant_id, batch_id, item_index);

CREATE TABLE org_reassignment_work_item (
    work_item_id    uuid         NOT NULL,
    tenant_id       varchar(64)  NOT NULL,
    organization_id uuid         NOT NULL,
    membership_id   uuid         NOT NULL,
    principal_id    uuid         NOT NULL,
    work_item_status varchar(24) NOT NULL,
    reason          varchar(500) NOT NULL,
    created_by      varchar(128) NOT NULL,
    created_at      timestamptz  NOT NULL,
    correlation_id  varchar(128) NOT NULL,
    CONSTRAINT pk_org_reassignment_work_item PRIMARY KEY (work_item_id),
    CONSTRAINT fk_org_reassignment_membership FOREIGN KEY (tenant_id, membership_id)
        REFERENCES org_membership (tenant_id, membership_id),
    CONSTRAINT fk_org_reassignment_organization FOREIGN KEY (tenant_id, organization_id)
        REFERENCES org_organization (tenant_id, organization_id),
    CONSTRAINT ck_org_reassignment_status CHECK (
        work_item_status IN ('OPEN', 'CLOSED')
    )
);

CREATE INDEX ix_org_reassignment_open
    ON org_reassignment_work_item (tenant_id, work_item_status, created_at, work_item_id);

CREATE TABLE org_structure_event (
    structure_event_id uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    organization_id    uuid         NOT NULL,
    event_type         varchar(40)  NOT NULL,
    resource_type      varchar(40)  NOT NULL,
    resource_id        uuid         NOT NULL,
    resource_version   bigint       NOT NULL,
    reason             varchar(500),
    actor_id           varchar(128) NOT NULL,
    request_digest     char(64)     NOT NULL,
    correlation_id     varchar(128) NOT NULL,
    occurred_at        timestamptz  NOT NULL,
    CONSTRAINT pk_org_structure_event PRIMARY KEY (structure_event_id),
    CONSTRAINT fk_org_structure_event_org FOREIGN KEY (tenant_id, organization_id)
        REFERENCES org_organization (tenant_id, organization_id),
    CONSTRAINT ck_org_structure_event_type CHECK (
        event_type IN (
            'ORGANIZATION_CREATED', 'UNIT_CREATED', 'UNIT_MOVED',
            'MEMBERSHIP_CREATED', 'MEMBERSHIP_TRANSFERRED', 'MEMBERSHIP_TERMINATED',
            'SYNC_BATCH_COMPLETED', 'EXTERNAL_OVERRIDE'
        )
    ),
    CONSTRAINT ck_org_structure_event_digest CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_org_structure_event_version CHECK (resource_version > 0)
);

CREATE INDEX ix_org_structure_event_org
    ON org_structure_event (tenant_id, organization_id, occurred_at, structure_event_id);

CREATE OR REPLACE FUNCTION org_reject_immutable_fact_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'org immutable fact table % does not allow %', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_org_structure_event_no_update
    BEFORE UPDATE OR DELETE ON org_structure_event
    FOR EACH ROW EXECUTE FUNCTION org_reject_immutable_fact_mutation();

CREATE TRIGGER trg_org_directory_sync_item_no_update
    BEFORE UPDATE OR DELETE ON org_directory_sync_item
    FOR EACH ROW EXECUTE FUNCTION org_reject_immutable_fact_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('organization.read', '读取企业组织目录', 'HIGH', now()),
    ('organization.manageStructure', '管理企业组织与单元结构', 'CRITICAL', now()),
    ('organization.manageMembership', '管理企业组织任职', 'CRITICAL', now()),
    ('organization.sync', '提交组织目录外部同步批次', 'CRITICAL', now()),
    ('organization.overrideExternal', '覆盖外部权威组织字段', 'CRITICAL', now())
ON CONFLICT (capability_code) DO NOTHING;
