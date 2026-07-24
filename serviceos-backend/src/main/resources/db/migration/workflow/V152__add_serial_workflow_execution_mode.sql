-- DEC-010 / DEC-020：新设计器发布的 SERIAL_V1 定义由数据库兜底保证单活跃节点。
-- 历史定义保留 LEGACY 模式，避免在无法证明旧并行工单迁移安全时改写其语义。

ALTER TABLE wfl_workflow_instance
    ADD COLUMN execution_mode varchar(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN current_node_instance_id uuid,
    ADD COLUMN current_node_code varchar(100),
    ADD COLUMN current_phase_code varchar(100),
    ADD CONSTRAINT ck_wfl_execution_mode CHECK (
        execution_mode IN ('LEGACY', 'SERIAL_V1')
    );

ALTER TABLE wfl_node_instance
    ADD COLUMN execution_mode varchar(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN execution_sequence integer NOT NULL DEFAULT 1,
    ADD CONSTRAINT ck_wfl_node_execution_mode CHECK (
        execution_mode IN ('LEGACY', 'SERIAL_V1')
    ),
    ADD CONSTRAINT ck_wfl_node_execution_sequence CHECK (execution_sequence >= 1);

-- Node 的执行模式必须来自所属 Workflow，禁止各插入路径自行猜测。
CREATE OR REPLACE FUNCTION wfl_inherit_execution_mode()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    SELECT execution_mode
      INTO NEW.execution_mode
      FROM wfl_workflow_instance
     WHERE tenant_id = NEW.tenant_id
       AND workflow_instance_id = NEW.workflow_instance_id;
    IF NEW.execution_mode IS NULL THEN
        RAISE EXCEPTION 'workflow instance execution mode not found';
    END IF;
    SELECT COALESCE(MAX(execution_sequence), 0) + 1
      INTO NEW.execution_sequence
     FROM wfl_node_instance
     WHERE tenant_id = NEW.tenant_id
       AND workflow_instance_id = NEW.workflow_instance_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_wfl_node_inherit_execution_mode
    BEFORE INSERT ON wfl_node_instance
    FOR EACH ROW EXECUTE FUNCTION wfl_inherit_execution_mode();

CREATE UNIQUE INDEX uq_wfl_serial_single_active_node
    ON wfl_node_instance (tenant_id, workflow_instance_id)
    WHERE execution_mode = 'SERIAL_V1' AND status IN ('ACTIVE', 'WAITING');

CREATE INDEX ix_wfl_serial_current
    ON wfl_workflow_instance (tenant_id, work_order_id, current_phase_code, current_node_code)
    WHERE execution_mode = 'SERIAL_V1' AND status = 'ACTIVE';

COMMENT ON COLUMN wfl_workflow_instance.execution_mode IS
    'SERIAL_V1 仅用于最终版设计器定义；LEGACY 保留历史冻结定义的原执行语义';
COMMENT ON INDEX uq_wfl_serial_single_active_node IS
    'SERIAL_V1 任意时刻最多一个 ACTIVE/WAITING Node Execution';
