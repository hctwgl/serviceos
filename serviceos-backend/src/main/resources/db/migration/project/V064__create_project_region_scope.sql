CREATE TABLE prj_project_region (
    project_region_id uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    project_id        uuid         NOT NULL,
    region_code       varchar(128) NOT NULL,
    valid_from        timestamptz  NOT NULL,
    valid_to          timestamptz,
    created_by        varchar(128) NOT NULL,
    created_at        timestamptz  NOT NULL,
    CONSTRAINT pk_prj_project_region PRIMARY KEY (project_region_id),
    CONSTRAINT fk_prj_project_region_project
        FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project (tenant_id, project_id) ON DELETE CASCADE,
    CONSTRAINT uq_prj_project_region_effective
        UNIQUE (tenant_id, project_id, region_code, valid_from),
    CONSTRAINT ck_prj_project_region_code CHECK (region_code = btrim(region_code) AND region_code <> ''),
    CONSTRAINT ck_prj_project_region_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX ix_prj_project_region_scope
    ON prj_project_region (tenant_id, region_code, valid_from, valid_to, project_id);
