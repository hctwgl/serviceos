# M409 Admin 关注项目待办/SLA 角标聚合

Status: Implemented  
Date: 2026-07-20

## 目标

关闭 Admin 运营工作台「关注项目」列表上的 `UI_DATA_GAP`：待办与 SLA 角标不得由前端二次聚合猜测，必须由服务端在 `GET /me/followed-projects` 读模型中 soft-gate 投影。

## 范围

- 扩展 `FollowedProjectItem`：
  - `activeWorkOrderCount` / `*Truncated`
  - `openReviewCount` / `*Truncated`
  - `openCorrectionCount` / `*Truncated`
  - `slaBreachedCount` / `*Truncated`
  - `openTodoCount`（可见审核+整改+SLA 超时合计）
- 列表查询在已通过 `project.read` 的项目上，分别调用已有 WorkOrder / Review / Correction / SLA 队列查询；缺能力返回 `null`，不伪造 0。
- Admin `WorkbenchPage` 展示「待办 / SLA / 工单」角标。

## 非目标

- 不引入经纬度距离（仓库仍无坐标事实）。
- 不改关注写路径事务边界；`PUT` 关注响应仍可不带角标（`withoutBadges`）。
- 不做精确全量 COUNT(*) 专用 SQL；角标以上限 100 页投影，超出时 `*Truncated=true`。

## 权限与数据范围

- Portal：`ADMIN` only。
- 项目可见性：逐项 `project.read`；失权清理既有逻辑保留。
- 角标 soft-gate：
  - `workOrder.read`
  - `evidence.review`
  - `evidence.read`
  - `sla.read`
- 各子查询仍走既有 Queue/Query 的 DataScope，不信任客户端 projectId 旁路。

## 契约

- OpenAPI `serviceos-core-v1` → `1.0.75`
- `FollowedProjectItem` 新增可选计数字段

## 证据

- `FollowedProjectPostgresIT`
- `FollowedProjectItemTest`
- Admin E2E `admin-project-workbench-product.spec.ts`
