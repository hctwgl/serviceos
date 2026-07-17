---
title: M146 Admin 外发交付队列筛选
status: Implemented
milestone: M146
lastUpdated: 2026-07-17
---

# M146 Admin 外发交付队列筛选

## 1. 范围

承接 M145，将已 Accepted/Implemented 的 API-06 §6 `GET /outbound-deliveries` 筛选接到 Admin：

```text
外发交付队列
→ status / businessMessageType / projectId / sourceWorkOrderId / sourceReviewCaseId
→ 查询（默认 status=UNKNOWN，与 OpenAPI 一致）
```

不新增 OpenAPI、不支持多状态 OR、不做 SavedView。

## 2. 实现要点

1. `OutboundQueuePage` 增加筛选表单，模式对齐 `SlaQueuePage`；
2. `listOutboundDeliveries` 参数类型化为 `OutboundDeliveryQueueQuery`；
3. Playwright 外发用例在 ACK 后按 `ACKNOWLEDGED` 筛选并见到交付链接。

## 3. 事务 / 授权 / 幂等

本切片为只读查询 UI：

- 授权仍由后端 `GET /outbound-deliveries` 的 Capability 与 Tenant/Project Scope 强制；
- 不引入写命令、不改变 OutboundDelivery 状态机；
- 省略 `status` 时服务端仍默认 `UNKNOWN`（UI 无“全部”选项）。

## 4. 明确未实现

- status 省略表示“全部”（服务端仍默认 UNKNOWN）；
- 多 status OR、SavedView / 通用 work-queues；
- Workspace INTEGRATION → Outbound Delivery 深链（M147 已实现）；
- 专用入站队列列表、原文下载、真实 sandbox。

## 5. 证据入口

- `serviceos-admin-web/src/pages/OutboundQueuePage.vue`
- `serviceos-admin-web/src/api/queues.ts`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `serviceos-architecture/testing/143-m146-admin-outbound-queue-filters-acceptance.md`
- `ADMIN-PILOT-08OQ`（`testing/admin-pilot-readiness-acceptance.md`）
