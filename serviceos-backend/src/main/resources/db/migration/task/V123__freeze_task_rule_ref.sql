-- M325：工作流节点冻结 RULE ruleKey；NULL 表示该 Task 不绑定规则资产。

ALTER TABLE tsk_task
    ADD COLUMN rule_ref varchar(120),
    ADD CONSTRAINT ck_tsk_rule_ref_workflow_context CHECK (
        rule_ref IS NULL OR workflow_definition_version_id IS NOT NULL
    );

COMMENT ON COLUMN tsk_task.rule_ref IS
    'M325：工作流节点冻结的 RULE ruleKey；NULL 表示该 Task 不绑定规则资产';
