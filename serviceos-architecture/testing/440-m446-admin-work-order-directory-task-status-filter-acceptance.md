---
title: M446 Admin 工单目录按任务状态筛选验收矩阵
version: 0.1.0
status: Implemented
milestone: M446
lastUpdated: 2026-07-21
---

# M446 Admin 工单目录按任务状态筛选验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | currentTaskStatus=READY | 仅命中当前任务 READY 的工单 | `listFiltersByCurrentTaskStatus` |
| A2 | RUNNING | 仅命中 RUNNING | 同上 |
| A3 | 非法枚举（SUCCEEDED） | IllegalArgumentException | 同上 |
| A4 | Admin | 筛选与列可见 | Playwright |

产品状态：`READY_FOR_REVIEW`。
