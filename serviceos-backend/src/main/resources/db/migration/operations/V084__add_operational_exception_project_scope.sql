-- M100：运营异常工作台按实时项目范围过滤；从工单/任务回填 project_id。

ALTER TABLE ops_operational_exception
    ADD COLUMN IF NOT EXISTS project_id uuid;

UPDATE ops_operational_exception exception
   SET project_id = work_order.project_id
  FROM wo_work_order work_order
 WHERE exception.project_id IS NULL
   AND exception.work_order_id IS NOT NULL
   AND work_order.tenant_id = exception.tenant_id
   AND work_order.id = exception.work_order_id;

UPDATE ops_operational_exception exception
   SET project_id = task.project_id
  FROM tsk_task task
 WHERE exception.project_id IS NULL
   AND exception.task_id IS NOT NULL
   AND task.tenant_id = exception.tenant_id
   AND task.task_id = exception.task_id;

ALTER TABLE ops_operational_exception
    ADD CONSTRAINT fk_ops_exception_project
        FOREIGN KEY (project_id) REFERENCES prj_project(project_id);

CREATE INDEX ix_ops_exception_project_queue
    ON ops_operational_exception (tenant_id, project_id, opened_at DESC, exception_id DESC)
    WHERE project_id IS NOT NULL;

COMMENT ON COLUMN ops_operational_exception.project_id IS
    'M100：项目范围权威；缺省时仅 TENANT 范围主体可见';
COMMENT ON INDEX ix_ops_exception_project_queue IS
    'M100：项目范围运营异常工作台稳定倒序游标';
