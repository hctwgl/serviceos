-- M28：自动恢复必须留下可核验的异常解决证据，并合法终止不再需要的人工接管任务。
ALTER TABLE tsk_task
    ADD COLUMN cancelled_at timestamptz,
    ADD COLUMN cancellation_reason_code varchar(100),
    ADD COLUMN cancellation_source_event_id uuid;

-- 兼容升级前已经存在的 CANCELLED 事实；新命令始终写入真实来源事件。
UPDATE tsk_task
   SET cancelled_at = COALESCE(completed_at, updated_at),
       cancellation_reason_code = 'LEGACY_CANCELLED',
       cancellation_source_event_id = md5(task_id::text || ':legacy-cancel')::uuid
 WHERE status = 'CANCELLED';

ALTER TABLE tsk_task
    ADD CONSTRAINT ck_tsk_task_cancellation_evidence CHECK (
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancellation_reason_code IS NOT NULL
            AND cancellation_source_event_id IS NOT NULL)
        OR
        (status <> 'CANCELLED'
            AND cancelled_at IS NULL
            AND cancellation_reason_code IS NULL
            AND cancellation_source_event_id IS NULL)
    );

ALTER TABLE ops_operational_exception
    ADD COLUMN resolution_code varchar(100),
    ADD COLUMN resolution_action_ref varchar(500),
    ADD COLUMN resolution_event_id uuid;

-- V009 允许旧数据只写 RESOLVED；升级时补成显式 legacy 证据，避免破坏在线迁移。
UPDATE ops_operational_exception
   SET resolution_code = 'LEGACY_RESOLUTION',
       resolution_action_ref = 'legacy:unknown',
       resolution_event_id = md5(exception_id::text || ':legacy-resolution')::uuid,
       resolved_at = COALESCE(resolved_at, opened_at)
 WHERE status = 'RESOLVED';

ALTER TABLE ops_operational_exception
    ADD CONSTRAINT ck_ops_exception_resolution_evidence CHECK (
        (status = 'OPEN'
            AND resolved_at IS NULL
            AND resolution_code IS NULL
            AND resolution_action_ref IS NULL
            AND resolution_event_id IS NULL)
        OR
        (status = 'RESOLVED'
            AND resolved_at IS NOT NULL
            AND resolution_code IS NOT NULL
            AND resolution_action_ref IS NOT NULL
            AND resolution_event_id IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_ops_exception_resolution_event
    ON ops_operational_exception (tenant_id, resolution_event_id)
    WHERE resolution_event_id IS NOT NULL;
