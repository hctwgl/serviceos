-- 仅删除 WO-DEMO-* 演示工单（不动结构、不动 ADMIN-PILOT 与非演示数据）
\set ON_ERROR_STOP on

DELETE FROM wo_work_order
 WHERE tenant_id = 'tenant-local'
   AND (
     external_order_code LIKE 'WO-DEMO-%'
     OR id::text LIKE 'd3500000-%'
   );
