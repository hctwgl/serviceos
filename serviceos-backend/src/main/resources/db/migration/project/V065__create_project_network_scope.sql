CREATE TABLE prj_project_network (
    project_network_id uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    project_id         uuid         NOT NULL,
    network_id         varchar(128) NOT NULL,
    valid_from         timestamptz  NOT NULL,
    valid_to           timestamptz,
    created_by         varchar(128) NOT NULL,
    created_at         timestamptz  NOT NULL,
    CONSTRAINT pk_prj_project_network PRIMARY KEY (project_network_id),
    CONSTRAINT fk_prj_project_network_project
        FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project (tenant_id, project_id) ON DELETE CASCADE,
    CONSTRAINT uq_prj_project_network_effective
        UNIQUE (tenant_id, project_id, network_id, valid_from),
    CONSTRAINT ck_prj_project_network_id CHECK (network_id = btrim(network_id) AND network_id <> ''),
    CONSTRAINT ck_prj_project_network_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX ix_prj_project_network_scope
    ON prj_project_network (tenant_id, network_id, valid_from, valid_to, project_id);
