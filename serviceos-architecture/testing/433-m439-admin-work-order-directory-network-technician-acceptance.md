---
title: M439 Admin 工单目录网点/师傅列验收矩阵
version: 0.1.0
status: Implemented
milestone: M439
lastUpdated: 2026-07-21
---

# M439 Admin 工单目录网点/师傅列验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 无 ACTIVE 服务责任 | 四字段均为 null | `WorkOrderQueryPostgresIT` 基线断言 |
| A2 | ACTIVE NETWORK + TECHNICIAN | 返回 ID 与目录显示名 | `listAndDetailExposeCurrentNetworkAndTechnicianFromActiveAssignment` |
| A3 | MVC 契约 | 响应含 `currentNetworkDisplayName` / `currentTechnicianDisplayName` | `WorkOrderControllerSecurityTest` |
| A4 | Admin 列 | 「网点/师傅」可见且展示中文显示名 | Playwright + 截图 |

产品状态：`READY_FOR_REVIEW`。
