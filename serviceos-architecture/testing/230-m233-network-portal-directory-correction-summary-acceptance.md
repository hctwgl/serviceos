---
title: M233 Network Portal 目录页资料整改案例服务端摘要验收矩阵
status: Accepted
milestone: M233
lastUpdated: 2026-07-17
---

# M233 验收标准：Network Portal 目录页资料整改案例服务端摘要旁载（ADR-071）

> 配套：architecture/246、ADR-071。OpenAPI **1.0.13**；catalog v16。

| ID | 场景 | 期望 | 证据 |
| --- | --- | --- | --- |
| M233-01 | NETWORK evidence.read + 目录任务整改 | work-orders/tasks 页 `corrections` 含摘要 | pass（PostgresIT） |
| M233-02 | 缺 evidence.read | JSON 省略 `corrections` | pass（PostgresIT） |
| M233-03 | 有能力但无整改 | `corrections=[]` | pass（PostgresIT） |
| M233-04 | 他网点任务整改不计入 | 仅本页 taskIds | pass（PostgresIT） |
| M233-05 | Admin Web 整改列展示/省略 | E2E | pass（E2E） |
| M233-06 | OpenAPI 1.0.13；Flyway 100/102；catalog v16 | 契约/预检 | pass（preflight） |

## A. 契约

- [x] `NetworkPortalWorkOrderPage` / `NetworkPortalTaskPage` 含可选 `corrections`（`$ref` `WorkOrderWorkspaceCorrectionCaseSummary`）。
- [x] Path 描述声明 NETWORK `evidence.read` 软门禁与页级 taskIds 范围。
- [x] OpenAPI 1.0.13；无新 path；catalog 仍 v16。

## B. 运行时

- [x] 缺 `evidence.read`：响应**省略** `corrections`（可有 technicians/appointments/contactAttempts）。
- [x] 有权且页内无案例：`corrections: []`。
- [x] 有权：仅当前页相关 taskIds；字段与 M225 工作台一致；排序 `createdAt` 升序。
- [x] 跨网 / 未知 taskId 不泄漏。

## C. Admin Web

- [x] 目录「整改」列读取页级 `corrections`，不按行请求工作台。
- [x] 缺旁载时不展示该列/不 N+1。

## D. 回归

- [x] M225 工作台 corrections 不变。
- [x] M230～M232 旁载字段可并存。
- [x] L3 `bash scripts/verify-local.sh` → EXIT:0。

## E. 明确未实现

目录 SLA 风险；目录 evidence；独立 NP Correction；通知；Portal ACK。
