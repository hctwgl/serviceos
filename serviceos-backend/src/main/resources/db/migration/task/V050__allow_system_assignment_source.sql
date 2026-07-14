-- M50: 允许 SYSTEM 来源的候选人批次（整改 Task 自动候选人）。

ALTER TABLE tsk_task_assignment_batch
    DROP CONSTRAINT IF EXISTS ck_tsk_assignment_batch_source;

ALTER TABLE tsk_task_assignment_batch
    ADD CONSTRAINT ck_tsk_assignment_batch_source
        CHECK (source_type IN ('ASSIGNEE_POLICY', 'MANUAL', 'SYSTEM'));
