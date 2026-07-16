-- M98：跨项目整改队列按 status + createdAt + id 公平稳定分页。

CREATE INDEX ix_evd_correction_case_queue_cursor
    ON evd_correction_case (tenant_id, status, created_at, correction_case_id);

COMMENT ON INDEX ix_evd_correction_case_queue_cursor IS
    'M98：整改案例队列范围化 FIFO 稳定游标';
