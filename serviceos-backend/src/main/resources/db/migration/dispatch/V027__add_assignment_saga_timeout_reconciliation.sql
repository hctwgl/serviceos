-- M27 为每个未终结激活阶段冻结明确截止时间，并把每次超时保存为不可覆盖的 occurrence。
ALTER TABLE dsp_service_assignment_activation_saga
    ADD COLUMN deadline_at timestamptz;

-- 历史非终态使用最后一次推进时间加默认 15 分钟回填；终态不再参与扫描。
UPDATE dsp_service_assignment_activation_saga
   SET deadline_at = updated_at + interval '15 minutes'
 WHERE stage NOT IN ('COMPLETED', 'ABORTED');

ALTER TABLE dsp_service_assignment_activation_saga
    ADD CONSTRAINT ck_dsp_saga_deadline CHECK (
        (stage IN ('COMPLETED', 'ABORTED') AND deadline_at IS NULL)
        OR (stage NOT IN ('COMPLETED', 'ABORTED') AND deadline_at IS NOT NULL)
    );

CREATE INDEX ix_dsp_assignment_saga_due
    ON dsp_service_assignment_activation_saga (deadline_at, activation_saga_id)
    WHERE stage NOT IN ('COMPLETED', 'ABORTED');

CREATE TABLE dsp_service_assignment_saga_timeout (
    timeout_id uuid NOT NULL,
    tenant_id varchar(64) NOT NULL,
    activation_saga_id uuid NOT NULL,
    service_assignment_id uuid NOT NULL,
    stage varchar(32) NOT NULL,
    saga_version bigint NOT NULL,
    deadline_at timestamptz NOT NULL,
    detected_at timestamptz NOT NULL,
    event_id uuid NOT NULL,
    error_code varchar(100) NOT NULL,
    CONSTRAINT pk_dsp_assignment_saga_timeout PRIMARY KEY (timeout_id),
    CONSTRAINT uq_dsp_assignment_saga_timeout_stage
        UNIQUE (tenant_id, activation_saga_id, stage, saga_version),
    CONSTRAINT uq_dsp_assignment_saga_timeout_event UNIQUE (event_id),
    CONSTRAINT fk_dsp_assignment_saga_timeout_saga FOREIGN KEY (activation_saga_id)
        REFERENCES dsp_service_assignment_activation_saga (activation_saga_id),
    CONSTRAINT fk_dsp_assignment_saga_timeout_assignment FOREIGN KEY (service_assignment_id)
        REFERENCES dsp_service_assignment (service_assignment_id),
    CONSTRAINT ck_dsp_assignment_saga_timeout_version CHECK (saga_version > 0)
);

CREATE INDEX ix_dsp_assignment_saga_timeout_source
    ON dsp_service_assignment_saga_timeout (tenant_id, activation_saga_id, detected_at);

-- 异常中心按 saga 聚合重复超时，同时保留最后检测时间与业务上下文。
ALTER TABLE ops_operational_exception
    ADD COLUMN work_order_id uuid,
    ADD COLUMN task_id uuid,
    ADD COLUMN occurrence_count integer NOT NULL DEFAULT 1,
    ADD COLUMN last_detected_at timestamptz;

UPDATE ops_operational_exception
   SET last_detected_at = opened_at
 WHERE last_detected_at IS NULL;

ALTER TABLE ops_operational_exception
    ALTER COLUMN last_detected_at SET NOT NULL,
    ADD CONSTRAINT ck_ops_exception_occurrence_count CHECK (occurrence_count > 0);
