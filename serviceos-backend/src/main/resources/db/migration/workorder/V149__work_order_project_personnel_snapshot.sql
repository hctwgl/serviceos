CREATE TABLE wo_project_personnel_snapshot (
    snapshot_id          uuid         NOT NULL,
    tenant_id            varchar(64)  NOT NULL,
    work_order_id        uuid         NOT NULL,
    position_code        varchar(40)  NOT NULL,
    principal_id         uuid,
    principal_name       varchar(200),
    source_assignment_id uuid,
    requested_region_code varchar(12) NOT NULL,
    matched_region_code  varchar(12),
    matched_region_name  varchar(120),
    match_status         varchar(24)  NOT NULL,
    inherited            boolean      NOT NULL,
    matched_at           timestamptz  NOT NULL,
    valid_from           timestamptz  NOT NULL,
    valid_to             timestamptz,
    adjustment_reason    varchar(500),
    snapshot_status      varchar(24)  NOT NULL,
    created_at           timestamptz  NOT NULL,
    CONSTRAINT pk_wo_project_personnel_snapshot PRIMARY KEY (snapshot_id),
    CONSTRAINT fk_wo_project_personnel_snapshot_work_order
        FOREIGN KEY (work_order_id)
        REFERENCES wo_work_order (id),
    CONSTRAINT ck_wo_project_personnel_position CHECK (
        position_code IN ('CUSTOMER_SERVICE_MANAGER', 'PROJECT_MANAGER', 'PROJECT_ASSISTANT')
    ),
    CONSTRAINT ck_wo_project_personnel_match_status CHECK (
        match_status IN ('ASSIGNED', 'MISSING', 'DATA_INCOMPLETE')
    ),
    CONSTRAINT ck_wo_project_personnel_snapshot_status CHECK (
        snapshot_status IN ('CURRENT', 'SUPERSEDED')
    ),
    CONSTRAINT ck_wo_project_personnel_assignment_shape CHECK (
        (match_status = 'ASSIGNED'
            AND principal_id IS NOT NULL
            AND principal_name IS NOT NULL
            AND source_assignment_id IS NOT NULL
            AND matched_region_code IS NOT NULL
            AND matched_region_name IS NOT NULL)
        OR
        (match_status = 'MISSING'
            AND principal_id IS NULL
            AND principal_name IS NULL
            AND source_assignment_id IS NULL
            AND matched_region_code IS NULL
            AND matched_region_name IS NULL)
        OR
        (match_status = 'DATA_INCOMPLETE'
            AND principal_id IS NOT NULL
            AND principal_name IS NULL
            AND source_assignment_id IS NOT NULL
            AND matched_region_code IS NOT NULL
            AND matched_region_name IS NOT NULL)
    ),
    CONSTRAINT ck_wo_project_personnel_validity CHECK (
        (snapshot_status = 'CURRENT' AND valid_to IS NULL)
        OR (snapshot_status = 'SUPERSEDED' AND valid_to IS NOT NULL AND valid_to > valid_from)
    )
);

CREATE UNIQUE INDEX uk_wo_project_personnel_current_position
    ON wo_project_personnel_snapshot (tenant_id, work_order_id, position_code)
    WHERE snapshot_status = 'CURRENT';

CREATE INDEX idx_wo_project_personnel_principal
    ON wo_project_personnel_snapshot (tenant_id, principal_id, work_order_id)
    WHERE snapshot_status = 'CURRENT' AND principal_id IS NOT NULL;

COMMENT ON TABLE wo_project_personnel_snapshot IS
'工单项目岗位人员快照。工单创建时按项目和标准行政区域固化客服经理、项目经理、项目助理；区域分工后续变化不会静默改写历史工单。';
COMMENT ON COLUMN wo_project_personnel_snapshot.snapshot_id IS '岗位人员快照标识。用于保存初始匹配及以后明确人工调整形成的历史版本。';
COMMENT ON COLUMN wo_project_personnel_snapshot.tenant_id IS '租户标识。与工单共同限定数据边界。';
COMMENT ON COLUMN wo_project_personnel_snapshot.work_order_id IS '所属工单标识。每个当前工单固定保留三个项目岗位的当前快照。';
COMMENT ON COLUMN wo_project_personnel_snapshot.position_code IS '项目固定岗位：客服经理、项目经理或项目助理。';
COMMENT ON COLUMN wo_project_personnel_snapshot.principal_id IS '匹配到的统一主体标识；岗位缺失时为空。仅作为来源引用，不随人员档案删除而丢失快照。';
COMMENT ON COLUMN wo_project_personnel_snapshot.principal_name IS '人员显示名快照。只在创建或明确调整时写入，不在页面查询时实时读取人员档案。';
COMMENT ON COLUMN wo_project_personnel_snapshot.source_assignment_id IS '产生本次匹配的项目区域岗位分工标识；岗位缺失时为空。';
COMMENT ON COLUMN wo_project_personnel_snapshot.requested_region_code IS '工单创建时用于匹配的标准行政区编码，优先使用区县，其次市、省。';
COMMENT ON COLUMN wo_project_personnel_snapshot.matched_region_code IS '实际命中的区域编码；岗位缺失时为空。';
COMMENT ON COLUMN wo_project_personnel_snapshot.matched_region_name IS '实际命中的区域中文名称快照；岗位缺失时为空。';
COMMENT ON COLUMN wo_project_personnel_snapshot.match_status IS '匹配结果：已匹配、岗位缺失或人员档案不完整。异常状态必须显式展示，不允许默认人员兜底。';
COMMENT ON COLUMN wo_project_personnel_snapshot.inherited IS '是否通过上级行政区域分工继承命中。岗位缺失时固定为 false。';
COMMENT ON COLUMN wo_project_personnel_snapshot.matched_at IS '执行项目区域人员匹配的时间。使用带时区时间。';
COMMENT ON COLUMN wo_project_personnel_snapshot.valid_from IS '本快照版本开始生效时间。';
COMMENT ON COLUMN wo_project_personnel_snapshot.valid_to IS '本快照版本结束生效时间；当前版本为空。';
COMMENT ON COLUMN wo_project_personnel_snapshot.adjustment_reason IS '人工调整岗位人员时的业务原因；初始自动匹配为空。';
COMMENT ON COLUMN wo_project_personnel_snapshot.snapshot_status IS '快照版本状态：当前有效或已被后续明确调整替代。';
COMMENT ON COLUMN wo_project_personnel_snapshot.created_at IS '本快照版本写入数据库的时间。';
COMMENT ON CONSTRAINT fk_wo_project_personnel_snapshot_work_order ON wo_project_personnel_snapshot IS
'保证岗位人员快照只能归属于真实存在的工单；tenant_id 仍由写入事务与查询条件显式执行租户隔离。';
COMMENT ON INDEX uk_wo_project_personnel_current_position IS
'同一工单同一项目岗位在任意时刻只允许一个当前快照，防止并发调整产生双重责任。';
