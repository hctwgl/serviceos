-- 项目团队与区域岗位分工属于项目日常运营主数据，不随履约配置版本发布。
-- 当前阶段只支持客服经理、项目经理、项目助理三个固定岗位；一个区域同一岗位只能有一名当前负责人。

CREATE TABLE prj_project_member (
    project_member_id uuid         NOT NULL,
    tenant_id         varchar(64)  NOT NULL,
    project_id        uuid         NOT NULL,
    principal_id      uuid         NOT NULL,
    member_status     varchar(24)  NOT NULL,
    valid_from        timestamptz  NOT NULL,
    valid_to          timestamptz,
    aggregate_version bigint       NOT NULL,
    created_by        varchar(128) NOT NULL,
    created_at        timestamptz  NOT NULL,
    ended_by          varchar(128),
    ended_at          timestamptz,
    end_reason        varchar(500),
    CONSTRAINT pk_prj_project_member PRIMARY KEY (project_member_id),
    CONSTRAINT uq_prj_project_member_scope
        UNIQUE (tenant_id, project_id, project_member_id, principal_id),
    CONSTRAINT fk_prj_project_member_project
        FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project (tenant_id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_prj_project_member_principal
        FOREIGN KEY (tenant_id, principal_id)
        REFERENCES idn_security_principal (tenant_id, principal_id),
    CONSTRAINT ck_prj_project_member_status
        CHECK (member_status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_prj_project_member_version
        CHECK (aggregate_version > 0),
    CONSTRAINT ck_prj_project_member_period
        CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_prj_project_member_end_shape
        CHECK (
            (member_status = 'ACTIVE'
                AND valid_to IS NULL AND ended_by IS NULL AND ended_at IS NULL AND end_reason IS NULL)
            OR
            (member_status = 'ENDED'
                AND valid_to IS NOT NULL AND ended_by IS NOT NULL
                AND ended_at IS NOT NULL AND length(btrim(end_reason)) > 0)
        )
);

CREATE UNIQUE INDEX uq_prj_project_member_active
    ON prj_project_member (tenant_id, project_id, principal_id)
    WHERE member_status = 'ACTIVE';

CREATE INDEX ix_prj_project_member_directory
    ON prj_project_member (tenant_id, project_id, member_status, created_at, project_member_id);

CREATE TABLE prj_project_region_personnel_assignment (
    assignment_id      uuid         NOT NULL,
    tenant_id          varchar(64)  NOT NULL,
    project_id         uuid         NOT NULL,
    region_code        varchar(32)  NOT NULL,
    position_code      varchar(40)  NOT NULL,
    project_member_id  uuid         NOT NULL,
    principal_id       uuid         NOT NULL,
    allow_inheritance  boolean      NOT NULL,
    assignment_status  varchar(24)  NOT NULL,
    valid_from         timestamptz  NOT NULL,
    valid_to           timestamptz,
    aggregate_version  bigint       NOT NULL,
    created_by         varchar(128) NOT NULL,
    created_at         timestamptz  NOT NULL,
    ended_by           varchar(128),
    ended_at           timestamptz,
    change_reason      varchar(500) NOT NULL,
    CONSTRAINT pk_prj_project_region_personnel_assignment PRIMARY KEY (assignment_id),
    CONSTRAINT fk_prj_region_personnel_project
        FOREIGN KEY (tenant_id, project_id)
        REFERENCES prj_project (tenant_id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_prj_region_personnel_region
        FOREIGN KEY (region_code)
        REFERENCES prj_region_catalog (region_code),
    CONSTRAINT fk_prj_region_personnel_member
        FOREIGN KEY (tenant_id, project_id, project_member_id, principal_id)
        REFERENCES prj_project_member (tenant_id, project_id, project_member_id, principal_id),
    CONSTRAINT ck_prj_region_personnel_position
        CHECK (position_code IN ('CUSTOMER_SERVICE_MANAGER', 'PROJECT_MANAGER', 'PROJECT_ASSISTANT')),
    CONSTRAINT ck_prj_region_personnel_status
        CHECK (assignment_status IN ('ACTIVE', 'ENDED')),
    CONSTRAINT ck_prj_region_personnel_version
        CHECK (aggregate_version > 0),
    CONSTRAINT ck_prj_region_personnel_period
        CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_prj_region_personnel_reason
        CHECK (length(btrim(change_reason)) > 0),
    CONSTRAINT ck_prj_region_personnel_end_shape
        CHECK (
            (assignment_status = 'ACTIVE'
                AND valid_to IS NULL AND ended_by IS NULL AND ended_at IS NULL)
            OR
            (assignment_status = 'ENDED'
                AND valid_to IS NOT NULL AND ended_by IS NOT NULL AND ended_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_prj_region_personnel_active
    ON prj_project_region_personnel_assignment (tenant_id, project_id, region_code, position_code)
    WHERE assignment_status = 'ACTIVE';

CREATE INDEX ix_prj_region_personnel_match
    ON prj_project_region_personnel_assignment
        (tenant_id, project_id, position_code, region_code, assignment_status);

CREATE INDEX ix_prj_region_personnel_member
    ON prj_project_region_personnel_assignment
        (tenant_id, project_id, principal_id, assignment_status);

INSERT INTO auth_capability (capability_code, capability_name, risk_level, created_at)
VALUES ('project.team.manage', '维护项目团队与区域岗位分工', 'HIGH', now())
ON CONFLICT (capability_code) DO NOTHING;

COMMENT ON TABLE prj_project_member IS
'项目成员目录。限定可被配置为当前项目客服经理、项目经理或项目助理的人员；人员变更不触发履约配置发布。';
COMMENT ON COLUMN prj_project_member.project_member_id IS '项目成员记录标识，不对普通业务页面展示。';
COMMENT ON COLUMN prj_project_member.tenant_id IS '成员所属租户标识，用于租户数据隔离。';
COMMENT ON COLUMN prj_project_member.project_id IS '成员参与的项目标识。';
COMMENT ON COLUMN prj_project_member.principal_id IS '统一主体目录中的人员标识；仅允许租户内有效主体。';
COMMENT ON COLUMN prj_project_member.member_status IS '项目成员状态：ACTIVE 为当前成员，ENDED 为已退出项目。';
COMMENT ON COLUMN prj_project_member.valid_from IS '成员关系生效时间，使用带时区时间。';
COMMENT ON COLUMN prj_project_member.valid_to IS '成员关系失效时间；当前有效成员为空。';
COMMENT ON COLUMN prj_project_member.aggregate_version IS '成员记录并发版本，从 1 开始递增。';
COMMENT ON COLUMN prj_project_member.created_by IS '创建成员关系的操作主体标识。';
COMMENT ON COLUMN prj_project_member.created_at IS '成员关系写入数据库的时间。';
COMMENT ON COLUMN prj_project_member.ended_by IS '结束成员关系的操作主体标识；当前有效成员为空。';
COMMENT ON COLUMN prj_project_member.ended_at IS '结束成员关系的操作时间；当前有效成员为空。';
COMMENT ON COLUMN prj_project_member.end_reason IS '结束成员关系的中文业务原因；当前有效成员为空。';
COMMENT ON CONSTRAINT ck_prj_project_member_end_shape ON prj_project_member IS
'保证当前成员没有终止信息，已退出成员具有完整的终止时间、操作人和原因。';
COMMENT ON INDEX uq_prj_project_member_active IS
'保证同一人员在同一项目中最多只有一条当前有效成员关系。';

COMMENT ON TABLE prj_project_region_personnel_assignment IS
'项目行政区域岗位人员分工。按项目、标准行政区和固定岗位确定工单创建时应冻结的项目协同人员。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.assignment_id IS '区域岗位分工记录标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.tenant_id IS '分工所属租户标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.project_id IS '分工所属项目标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.region_code IS '标准行政区编码，必须来自项目行政区目录。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.position_code IS
'固定项目岗位：客服经理、项目经理或项目助理。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.project_member_id IS
'被指派人员在当前项目中的有效成员关系标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.principal_id IS '被指派人员的统一主体标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.allow_inheritance IS
'是否允许下级行政区在没有更精确配置时继承本负责人。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.assignment_status IS
'分工状态：ACTIVE 为当前有效，ENDED 为已被明确替换。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.valid_from IS '分工生效时间。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.valid_to IS '分工失效时间；当前有效分工为空。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.aggregate_version IS '分工记录并发版本。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.created_by IS '创建分工的操作主体标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.created_at IS '分工写入数据库的时间。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.ended_by IS '结束分工的操作主体标识。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.ended_at IS '结束分工的操作时间。';
COMMENT ON COLUMN prj_project_region_personnel_assignment.change_reason IS
'本次新增或替换区域岗位负责人的中文业务原因。';
COMMENT ON CONSTRAINT ck_prj_region_personnel_position ON prj_project_region_personnel_assignment IS
'限制当前阶段只使用客服经理、项目经理和项目助理三个系统固定岗位。';
COMMENT ON INDEX uq_prj_region_personnel_active IS
'保证同一项目、同一行政区、同一岗位最多只有一名当前负责人。';
COMMENT ON INDEX ix_prj_region_personnel_match IS
'支持工单按项目、行政区层级和岗位解析项目协同人员。';
