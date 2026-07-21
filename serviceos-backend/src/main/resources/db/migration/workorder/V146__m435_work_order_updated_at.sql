-- M435：工单聚合独立 updated_at，关闭 Admin 目录「更新时间」对 received_at 的 MVP 映射。
-- DEFAULT now() 仅服务显式列清单遗漏的夹具/种子插入；生产命令路径始终写入业务时钟。

ALTER TABLE wo_work_order
    ADD COLUMN IF NOT EXISTS updated_at timestamptz;

UPDATE wo_work_order
   SET updated_at = GREATEST(
           received_at,
           COALESCE(activated_at, received_at),
           COALESCE(fulfilled_at, received_at),
           COALESCE(cancelled_at, received_at),
           COALESCE(reopened_at, received_at)
       )
 WHERE updated_at IS NULL;

ALTER TABLE wo_work_order
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET NOT NULL;

COMMENT ON COLUMN wo_work_order.updated_at IS
    'M435：工单聚合最近一次写路径更新时间；与 received_at 独立';
