-- M91：按工单实时组合 Integration 工作区，保持 tenant/project scope 与稳定顺序。

CREATE INDEX ix_int_outbound_delivery_work_order
    ON int_outbound_delivery (
        tenant_id, project_id, source_work_order_id, created_at, delivery_id
    );

COMMENT ON INDEX ix_int_outbound_delivery_work_order IS
    'M91：工单 Integration 工作区外发 Delivery 稳定查询';
