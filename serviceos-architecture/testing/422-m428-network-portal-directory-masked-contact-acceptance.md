---
title: M428 Network Portal 目录页脱敏客户联系验收矩阵
version: 0.1.0
status: Implemented
milestone: M428
lastUpdated: 2026-07-21
---

# M428 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 工单目录脱敏 | listWorkOrders items 含 masked*；无完整手机/地址 | `NetworkPortalReadPostgresIT` |
| A2 | 任务目录脱敏 | listTasks items 含所属工单 masked* | Playwright M428-02 |
| A3 | UI 展示 | Network 工单目录展示客户/电话/地址列 | Playwright M428-01 + 截图 |
| A4 | 契约 | OpenAPI 1.0.92；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
