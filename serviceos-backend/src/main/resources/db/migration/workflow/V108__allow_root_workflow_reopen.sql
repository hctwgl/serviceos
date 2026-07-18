-- M279：取消后允许重开并创建新的 ROOT 流程实例；唯一约束仅覆盖未终态根流程。

DROP INDEX IF EXISTS uq_wfl_root_work_order;

CREATE UNIQUE INDEX uq_wfl_root_work_order_open
    ON wfl_workflow_instance (tenant_id, work_order_id)
    WHERE instance_role = 'ROOT' AND status IN ('ACTIVE', 'SUSPENDED');
