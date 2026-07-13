CREATE TABLE prj_project (
    project_id         uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    project_code       varchar(64)  NOT NULL,
    client_id          varchar(128) NOT NULL,
    project_name       varchar(200) NOT NULL,
    starts_on          date         NOT NULL,
    ends_on            date,
    project_status     varchar(24)  NOT NULL,
    aggregate_version  bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz,
    CONSTRAINT pk_prj_project PRIMARY KEY (project_id),
    CONSTRAINT uq_prj_project_code UNIQUE (tenant_id, project_code),
    CONSTRAINT ck_prj_project_status
        CHECK (project_status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_prj_project_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_prj_project_dates CHECK (ends_on IS NULL OR ends_on >= starts_on)
);

CREATE INDEX ix_prj_project_client
    ON prj_project (tenant_id, client_id, project_status);
