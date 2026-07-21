---
title: M431 Admin 工单目录服务区域中文名验收矩阵
version: 0.1.0
status: Implemented
milestone: M431
lastUpdated: 2026-07-21
---

# M431 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 目录命中 | 区域列展示中文省/市/区名 | Playwright |
| A2 | 未命中回退 | 目录无该码时仍显示国标码 | 实现回退逻辑 |
| A3 | 契约稳定 | OpenAPI 仍 1.0.93；无 Flyway | 契约未改 |

产品状态：`READY_FOR_REVIEW`。
