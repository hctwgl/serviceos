---
title: M451 Admin 工单目录异常摘要深链
version: 0.1.0
status: Implemented
milestone: M451
lastUpdated: 2026-07-21
relatedMilestones: [M165, M450]
openapiVersion: "1.0.112"
---

# M451 Admin 工单目录异常摘要深链

## 1. 目标

关闭 Admin 工单目录「异常摘要」列到运营异常队列的深链：有 OPEN 计数时可一键打开队列并水合筛选。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **不变（1.0.112）** |
| Backend | 无变更；复用 M450 `exceptionSummaries` |
| Admin Web | `openCount>0` 时 `RouterLink` → `ADMIN.EXCEPTION.QUEUE?workOrderId=&status=OPEN`（与工作区 M165 同口径） |
| 证据 | Playwright |

## 3. 边界

- soft-omit / 暂无：纯文本，无链接
- 不新增筛选参数、不发明异常详情页直达（多 OPEN 时进队列）
- 无 Flyway、无新 capability

## 4. 明确未实现

- 目录异常筛选 query 参数（母版「更多筛选」未列）
- 单异常详情直达（需旁载 exceptionId）
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
