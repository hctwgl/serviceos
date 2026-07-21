---
title: M429 Admin 工单目录脱敏客户联系验收矩阵
version: 0.1.0
status: Implemented
milestone: M429
lastUpdated: 2026-07-21
---

# M429 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 目录脱敏 | list items 含 masked*；无完整手机/地址 | `WorkOrderQueryPostgresIT` |
| A2 | 详情对齐 | get 返回相同 masked* | 同上 |
| A3 | UI 展示 | 工单中心客户列展示脱敏三字段 | Playwright 视觉基线 |
| A4 | 契约 | OpenAPI 1.0.93；无 Flyway | 契约 diff |

产品状态：`READY_FOR_REVIEW`。
