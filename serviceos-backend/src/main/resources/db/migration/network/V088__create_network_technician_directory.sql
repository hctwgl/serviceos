-- M185：合作组织、ServiceNetwork、网点成员、师傅档案/关系与资质。

CREATE TABLE net_partner_organization (
    partner_organization_id uuid         NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    partner_code            varchar(64)  NOT NULL,
    partner_name            varchar(200) NOT NULL,
    partner_status          varchar(24)  NOT NULL,
    aggregate_version       bigint       NOT NULL,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    CONSTRAINT pk_net_partner_organization PRIMARY KEY (partner_organization_id),
    CONSTRAINT uq_net_partner_organization_tenant UNIQUE (tenant_id, partner_organization_id),
    CONSTRAINT uq_net_partner_organization_code UNIQUE (tenant_id, partner_code),
    CONSTRAINT ck_net_partner_organization_status CHECK (partner_status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_net_partner_organization_version CHECK (aggregate_version > 0)
);

CREATE TABLE net_service_network (
    service_network_id      uuid         NOT NULL,
    tenant_id               varchar(64)  NOT NULL,
    partner_organization_id uuid         NOT NULL,
    network_code            varchar(64)  NOT NULL,
    network_name            varchar(200) NOT NULL,
    network_status          varchar(24)  NOT NULL,
    aggregate_version       bigint       NOT NULL,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    deactivated_at          timestamptz,
    deactivated_by          varchar(128),
    deactivate_reason       varchar(500),
    CONSTRAINT pk_net_service_network PRIMARY KEY (service_network_id),
    CONSTRAINT uq_net_service_network_tenant UNIQUE (tenant_id, service_network_id),
    CONSTRAINT uq_net_service_network_code UNIQUE (tenant_id, network_code),
    CONSTRAINT fk_net_service_network_partner FOREIGN KEY (tenant_id, partner_organization_id)
        REFERENCES net_partner_organization (tenant_id, partner_organization_id),
    CONSTRAINT ck_net_service_network_status CHECK (
        network_status IN ('ACTIVE', 'DEACTIVATED')
    ),
    CONSTRAINT ck_net_service_network_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_net_service_network_deactivate CHECK (
        (network_status = 'ACTIVE' AND deactivated_at IS NULL
            AND deactivated_by IS NULL AND deactivate_reason IS NULL)
        OR
        (network_status = 'DEACTIVATED' AND deactivated_at IS NOT NULL
            AND deactivated_by IS NOT NULL AND deactivate_reason IS NOT NULL)
    )
);

CREATE INDEX ix_net_service_network_partner
    ON net_service_network (tenant_id, partner_organization_id, network_status);

CREATE TABLE net_network_membership (
    membership_id     uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    service_network_id uuid        NOT NULL,
    principal_id      uuid         NOT NULL,
    membership_role   varchar(40)  NOT NULL,
    membership_status varchar(24)  NOT NULL,
    valid_from        timestamptz  NOT NULL,
    valid_to          timestamptz,
    invited_by        varchar(128) NOT NULL,
    created_at        timestamptz  NOT NULL,
    terminated_by     varchar(128),
    terminated_at     timestamptz,
    terminate_reason  varchar(500),
    aggregate_version bigint       NOT NULL,
    CONSTRAINT pk_net_network_membership PRIMARY KEY (membership_id),
    CONSTRAINT uq_net_network_membership_tenant UNIQUE (tenant_id, membership_id),
    CONSTRAINT fk_net_network_membership_network FOREIGN KEY (tenant_id, service_network_id)
        REFERENCES net_service_network (tenant_id, service_network_id),
    CONSTRAINT ck_net_network_membership_role CHECK (
        membership_role IN ('MANAGER', 'STAFF')
    ),
    CONSTRAINT ck_net_network_membership_status CHECK (
        membership_status IN ('ACTIVE', 'TERMINATED')
    ),
    CONSTRAINT ck_net_network_membership_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_net_network_membership_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_net_network_membership_terminate CHECK (
        (membership_status = 'ACTIVE' AND valid_to IS NULL
            AND terminated_by IS NULL AND terminated_at IS NULL AND terminate_reason IS NULL)
        OR
        (membership_status = 'TERMINATED' AND valid_to IS NOT NULL
            AND terminated_by IS NOT NULL AND terminated_at IS NOT NULL AND terminate_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_net_network_membership_active
    ON net_network_membership (tenant_id, service_network_id, principal_id)
    WHERE membership_status = 'ACTIVE';

CREATE INDEX ix_net_network_membership_principal
    ON net_network_membership (tenant_id, principal_id, membership_status);

CREATE TABLE net_technician_profile (
    technician_profile_id uuid         NOT NULL,
    tenant_id             varchar(64)  NOT NULL,
    principal_id          uuid         NOT NULL,
    display_name          varchar(200) NOT NULL,
    profile_status        varchar(24)  NOT NULL,
    aggregate_version     bigint       NOT NULL,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    disabled_at           timestamptz,
    disabled_by           varchar(128),
    disabled_reason       varchar(500),
    CONSTRAINT pk_net_technician_profile PRIMARY KEY (technician_profile_id),
    CONSTRAINT uq_net_technician_profile_tenant UNIQUE (tenant_id, technician_profile_id),
    CONSTRAINT uq_net_technician_profile_principal UNIQUE (tenant_id, principal_id),
    CONSTRAINT ck_net_technician_profile_status CHECK (
        profile_status IN ('ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_net_technician_profile_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_net_technician_profile_disabled CHECK (
        (profile_status = 'ACTIVE' AND disabled_at IS NULL
            AND disabled_by IS NULL AND disabled_reason IS NULL)
        OR
        (profile_status = 'DISABLED' AND disabled_at IS NOT NULL
            AND disabled_by IS NOT NULL AND disabled_reason IS NOT NULL)
    )
);

CREATE TABLE net_network_technician_membership (
    membership_id         uuid         NOT NULL,
    tenant_id             varchar(64)  NOT NULL,
    service_network_id    uuid         NOT NULL,
    technician_profile_id uuid         NOT NULL,
    membership_status     varchar(24)  NOT NULL,
    valid_from            timestamptz  NOT NULL,
    valid_to              timestamptz,
    created_by            varchar(128) NOT NULL,
    created_at            timestamptz  NOT NULL,
    terminated_by         varchar(128),
    terminated_at         timestamptz,
    terminate_reason      varchar(500),
    aggregate_version     bigint       NOT NULL,
    CONSTRAINT pk_net_network_technician_membership PRIMARY KEY (membership_id),
    CONSTRAINT uq_net_ntm_tenant UNIQUE (tenant_id, membership_id),
    CONSTRAINT fk_net_ntm_network FOREIGN KEY (tenant_id, service_network_id)
        REFERENCES net_service_network (tenant_id, service_network_id),
    CONSTRAINT fk_net_ntm_technician FOREIGN KEY (tenant_id, technician_profile_id)
        REFERENCES net_technician_profile (tenant_id, technician_profile_id),
    CONSTRAINT ck_net_ntm_status CHECK (membership_status IN ('ACTIVE', 'TERMINATED')),
    CONSTRAINT ck_net_ntm_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_net_ntm_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_net_ntm_terminate CHECK (
        (membership_status = 'ACTIVE' AND valid_to IS NULL
            AND terminated_by IS NULL AND terminated_at IS NULL AND terminate_reason IS NULL)
        OR
        (membership_status = 'TERMINATED' AND valid_to IS NOT NULL
            AND terminated_by IS NOT NULL AND terminated_at IS NOT NULL AND terminate_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_net_ntm_active
    ON net_network_technician_membership (tenant_id, service_network_id, technician_profile_id)
    WHERE membership_status = 'ACTIVE';

CREATE INDEX ix_net_ntm_technician
    ON net_network_technician_membership (tenant_id, technician_profile_id, membership_status);

CREATE TABLE net_technician_qualification (
    qualification_id      uuid         NOT NULL,
    tenant_id             varchar(64)  NOT NULL,
    technician_profile_id uuid         NOT NULL,
    qualification_code    varchar(64)  NOT NULL,
    qualification_status  varchar(24)  NOT NULL,
    valid_from            timestamptz  NOT NULL,
    valid_to              timestamptz,
    submitted_by          varchar(128) NOT NULL,
    submitted_at          timestamptz  NOT NULL,
    decided_by            varchar(128),
    decided_at            timestamptz,
    decision_reason       varchar(500),
    aggregate_version     bigint       NOT NULL,
    CONSTRAINT pk_net_technician_qualification PRIMARY KEY (qualification_id),
    CONSTRAINT uq_net_qualification_tenant UNIQUE (tenant_id, qualification_id),
    CONSTRAINT fk_net_qualification_technician FOREIGN KEY (tenant_id, technician_profile_id)
        REFERENCES net_technician_profile (tenant_id, technician_profile_id),
    CONSTRAINT ck_net_qualification_status CHECK (
        qualification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT ck_net_qualification_period CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_net_qualification_version CHECK (aggregate_version > 0),
    CONSTRAINT ck_net_qualification_decision CHECK (
        (qualification_status = 'PENDING'
            AND decided_by IS NULL AND decided_at IS NULL AND decision_reason IS NULL)
        OR
        (qualification_status IN ('APPROVED', 'REJECTED', 'EXPIRED')
            AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE INDEX ix_net_qualification_technician
    ON net_technician_qualification (tenant_id, technician_profile_id, qualification_status, valid_to);

CREATE TABLE net_clearance_work_item (
    work_item_id          uuid         NOT NULL,
    tenant_id             varchar(64)  NOT NULL,
    subject_type          varchar(40)  NOT NULL,
    service_network_id    uuid,
    technician_profile_id uuid,
    work_item_status      varchar(24)  NOT NULL,
    reason                varchar(500) NOT NULL,
    open_task_count       integer      NOT NULL DEFAULT 0,
    open_appointment_count integer     NOT NULL DEFAULT 0,
    open_visit_count      integer      NOT NULL DEFAULT 0,
    active_assignment_count integer    NOT NULL DEFAULT 0,
    offline_package_count integer      NOT NULL DEFAULT 0,
    created_by            varchar(128) NOT NULL,
    created_at            timestamptz  NOT NULL,
    correlation_id        varchar(128) NOT NULL,
    CONSTRAINT pk_net_clearance_work_item PRIMARY KEY (work_item_id),
    CONSTRAINT ck_net_clearance_subject CHECK (
        subject_type IN ('SERVICE_NETWORK', 'TECHNICIAN_PROFILE')
    ),
    CONSTRAINT ck_net_clearance_status CHECK (work_item_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_net_clearance_counts CHECK (
        open_task_count >= 0 AND open_appointment_count >= 0
        AND open_visit_count >= 0 AND active_assignment_count >= 0
        AND offline_package_count >= 0
    )
);

CREATE INDEX ix_net_clearance_open
    ON net_clearance_work_item (tenant_id, work_item_status, created_at, work_item_id);

CREATE TABLE net_directory_event (
    directory_event_id uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    event_type         varchar(40)  NOT NULL,
    resource_type      varchar(40)  NOT NULL,
    resource_id        uuid         NOT NULL,
    resource_version   bigint       NOT NULL,
    reason             varchar(500),
    actor_id           varchar(128) NOT NULL,
    request_digest     char(64)     NOT NULL,
    correlation_id     varchar(128) NOT NULL,
    occurred_at        timestamptz  NOT NULL,
    CONSTRAINT pk_net_directory_event PRIMARY KEY (directory_event_id),
    CONSTRAINT ck_net_directory_event_type CHECK (
        event_type IN (
            'PARTNER_CREATED', 'NETWORK_CREATED', 'NETWORK_DEACTIVATED',
            'MEMBERSHIP_INVITED', 'MEMBERSHIP_TERMINATED',
            'TECHNICIAN_CREATED', 'TECHNICIAN_DISABLED', 'TECHNICIAN_ENABLED',
            'TECHNICIAN_MEMBERSHIP_CREATED', 'TECHNICIAN_MEMBERSHIP_TERMINATED',
            'QUALIFICATION_SUBMITTED', 'QUALIFICATION_DECIDED'
        )
    ),
    CONSTRAINT ck_net_directory_event_digest CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_net_directory_event_version CHECK (resource_version > 0)
);

CREATE OR REPLACE FUNCTION net_reject_immutable_fact_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'network directory immutable fact cannot be changed';
END;
$$;

CREATE TRIGGER trg_net_directory_event_immutable
    BEFORE UPDATE OR DELETE ON net_directory_event
    FOR EACH ROW EXECUTE FUNCTION net_reject_immutable_fact_mutation();

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES
    ('network.read', '读取网点与师傅目录', 'HIGH', now()),
    ('network.managePartner', '管理合作组织', 'CRITICAL', now()),
    ('network.manageNetwork', '管理 ServiceNetwork 与清退', 'CRITICAL', now()),
    ('network.manageMembership', '邀请/维护网点成员', 'HIGH', now()),
    ('network.manageTechnician', '维护师傅档案与网点服务关系', 'CRITICAL', now()),
    ('network.reviewQualification', '总部审核师傅资质', 'CRITICAL', now())
ON CONFLICT (capability_code) DO NOTHING;
