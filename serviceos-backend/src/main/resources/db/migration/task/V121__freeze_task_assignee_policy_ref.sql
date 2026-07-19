-- M323：工作流节点冻结 ASSIGNEE_POLICY policyKey；NULL 表示该 Task 不自动解析候选人。

ALTER TABLE tsk_task
    ADD COLUMN assignee_policy_ref varchar(120),
    ADD CONSTRAINT ck_tsk_assignee_policy_ref_workflow_context CHECK (
        assignee_policy_ref IS NULL OR workflow_definition_version_id IS NOT NULL
    );

COMMENT ON COLUMN tsk_task.assignee_policy_ref IS
    'M323：工作流节点冻结的 ASSIGNEE_POLICY policyKey；NULL 表示该 Task 不启用策略自动候选';
