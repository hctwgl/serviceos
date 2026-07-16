-- M97：跨项目审核队列按 status + createdAt + id 公平稳定分页。

CREATE INDEX ix_evd_review_case_queue_cursor
    ON evd_review_case (tenant_id, status, created_at, review_case_id);

COMMENT ON INDEX ix_evd_review_case_queue_cursor IS
    'M97：审核案例队列范围化 FIFO 稳定游标';
