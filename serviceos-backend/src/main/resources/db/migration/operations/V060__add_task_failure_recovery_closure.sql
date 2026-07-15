-- M60：记录明确领域恢复事实，使 Task 最终失败事件与恢复事件任意消费顺序都不会留下假告警。

CREATE TABLE ops_task_failure_recovery (
    tenant_id varchar(64) NOT NULL,
    source_task_id uuid NOT NULL,
    source_task_type varchar(120) NOT NULL,
    recovery_type varchar(100) NOT NULL,
    recovery_ref varchar(180) NOT NULL,
    recovery_event_id uuid NOT NULL,
    recovered_at timestamptz NOT NULL,
    correlation_id varchar(128) NOT NULL,
    CONSTRAINT pk_ops_task_failure_recovery PRIMARY KEY (tenant_id, source_task_id),
    CONSTRAINT fk_ops_task_failure_recovery_task
        FOREIGN KEY (source_task_id) REFERENCES tsk_task(task_id),
    CONSTRAINT ck_ops_task_failure_recovery_type CHECK (
        recovery_type = 'OUTBOUND_DELIVERY_ACKNOWLEDGED'),
    CONSTRAINT ck_ops_task_failure_source_type CHECK (
        source_task_type = 'integration.byd.submit-review')
);

CREATE INDEX ix_ops_task_failure_recovery_ref
    ON ops_task_failure_recovery (tenant_id, recovery_type, recovery_ref);

CREATE OR REPLACE FUNCTION ops_validate_task_failure_recovery_source()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM tsk_task task
         WHERE task.task_id = NEW.source_task_id
           AND task.tenant_id = NEW.tenant_id
           AND task.task_type = NEW.source_task_type
           AND task.task_kind = 'AUTOMATED') THEN
        RAISE EXCEPTION 'task failure recovery source identity mismatch';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_ops_task_failure_recovery_source
    BEFORE INSERT ON ops_task_failure_recovery
    FOR EACH ROW EXECUTE FUNCTION ops_validate_task_failure_recovery_source();

CREATE OR REPLACE FUNCTION ops_reject_task_failure_recovery_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'task failure recovery fact is immutable';
END;
$$;

CREATE TRIGGER trg_ops_task_failure_recovery_immutable
    BEFORE UPDATE OR DELETE ON ops_task_failure_recovery
    FOR EACH ROW EXECUTE FUNCTION ops_reject_task_failure_recovery_mutation();

COMMENT ON TABLE ops_task_failure_recovery IS
    'M60：Task 最终失败的不可变领域恢复事实，用于乱序消费时抑制或关闭假告警';
