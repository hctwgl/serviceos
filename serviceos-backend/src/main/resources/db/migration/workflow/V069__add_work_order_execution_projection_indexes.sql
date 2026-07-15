CREATE INDEX ix_wfl_stage_work_order_sequence
    ON wfl_stage_instance (tenant_id, work_order_id, sequence_no, stage_instance_id);

CREATE INDEX ix_tsk_work_order_created_cursor
    ON tsk_task (tenant_id, work_order_id, created_at, task_id)
    WHERE work_order_id IS NOT NULL;
