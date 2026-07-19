-- M324：工作流节点冻结 DISPATCH policyKey；NULL 表示该 Task 不自动走系统派单。

ALTER TABLE tsk_task
    ADD COLUMN dispatch_policy_ref varchar(120),
    ADD CONSTRAINT ck_tsk_dispatch_policy_ref_workflow_context CHECK (
        dispatch_policy_ref IS NULL OR workflow_definition_version_id IS NOT NULL
    );

COMMENT ON COLUMN tsk_task.dispatch_policy_ref IS
    'M324：工作流节点冻结的 DISPATCH policyKey；NULL 表示该 Task 不启用系统派单策略';
