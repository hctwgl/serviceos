INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('project.reviseScopeRelations', '修订项目区域与网点关系', 'HIGH', now());

ALTER TABLE prj_project_region
    ADD COLUMN ended_by varchar(128),
    ADD COLUMN ended_at timestamptz;

ALTER TABLE prj_project_region
    DROP CONSTRAINT ck_prj_project_region_period,
    ADD CONSTRAINT ck_prj_project_region_period
        CHECK (valid_to IS NULL OR valid_to >= valid_from),
    ADD CONSTRAINT ck_prj_project_region_end_audit CHECK (
        (valid_to IS NULL AND ended_by IS NULL AND ended_at IS NULL)
        OR (valid_to IS NOT NULL AND ended_by IS NOT NULL AND ended_at = valid_to)
    );

CREATE UNIQUE INDEX uq_prj_project_region_open
    ON prj_project_region (tenant_id, project_id, region_code)
    WHERE valid_to IS NULL;

ALTER TABLE prj_project_network
    ADD COLUMN ended_by varchar(128),
    ADD COLUMN ended_at timestamptz;

ALTER TABLE prj_project_network
    DROP CONSTRAINT ck_prj_project_network_period,
    ADD CONSTRAINT ck_prj_project_network_period
        CHECK (valid_to IS NULL OR valid_to >= valid_from),
    ADD CONSTRAINT ck_prj_project_network_end_audit CHECK (
        (valid_to IS NULL AND ended_by IS NULL AND ended_at IS NULL)
        OR (valid_to IS NOT NULL AND ended_by IS NOT NULL AND ended_at = valid_to)
    );

CREATE UNIQUE INDEX uq_prj_project_network_open
    ON prj_project_network (tenant_id, project_id, network_id)
    WHERE valid_to IS NULL;

CREATE TABLE prj_project_scope_revision (
    revision_id          uuid         NOT NULL,
    tenant_id            varchar(64)  NOT NULL,
    project_id           uuid         NOT NULL,
    expected_version     bigint       NOT NULL,
    aggregate_version    bigint       NOT NULL,
    region_codes         jsonb        NOT NULL,
    network_ids          jsonb        NOT NULL,
    added_region_codes   jsonb        NOT NULL,
    removed_region_codes jsonb        NOT NULL,
    added_network_ids    jsonb        NOT NULL,
    removed_network_ids  jsonb        NOT NULL,
    reason               varchar(500) NOT NULL,
    revised_by           varchar(128) NOT NULL,
    revised_at           timestamptz  NOT NULL,
    CONSTRAINT pk_prj_project_scope_revision PRIMARY KEY (revision_id),
    CONSTRAINT fk_prj_project_scope_revision_project
        FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project (tenant_id, project_id) ON DELETE RESTRICT,
    CONSTRAINT uq_prj_project_scope_revision_version
        UNIQUE (tenant_id, project_id, aggregate_version),
    CONSTRAINT ck_prj_project_scope_revision_version
        CHECK (expected_version >= 1 AND aggregate_version = expected_version + 1),
    CONSTRAINT ck_prj_project_scope_revision_arrays CHECK (
        jsonb_typeof(region_codes) = 'array'
        AND jsonb_typeof(network_ids) = 'array'
        AND jsonb_typeof(added_region_codes) = 'array'
        AND jsonb_typeof(removed_region_codes) = 'array'
        AND jsonb_typeof(added_network_ids) = 'array'
        AND jsonb_typeof(removed_network_ids) = 'array'
    ),
    CONSTRAINT ck_prj_project_scope_revision_reason
        CHECK (reason = btrim(reason) AND reason <> '')
);

CREATE OR REPLACE FUNCTION prj_reject_scope_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'project scope revision is immutable';
END;
$$;

CREATE TRIGGER trg_prj_scope_revision_immutable
    BEFORE UPDATE OR DELETE ON prj_project_scope_revision
    FOR EACH ROW EXECUTE FUNCTION prj_reject_scope_revision_mutation();
