-- M99：跨项目外发交付队列按 status + createdAt + id 公平稳定分页。

CREATE INDEX ix_int_outbound_delivery_queue_cursor
    ON int_outbound_delivery (tenant_id, status, created_at, delivery_id);

COMMENT ON INDEX ix_int_outbound_delivery_queue_cursor IS
    'M99：外发交付队列范围化 FIFO 稳定游标';
