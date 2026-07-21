---
title: M450 Admin 工单目录异常摘要列
version: 0.1.0
status: Implemented
milestone: M450
lastUpdated: 2026-07-21
relatedMilestones: [M434, M449]
openapiVersion: "1.0.112"
---

# M450 Admin 工单目录异常摘要列

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「异常摘要」列：页级旁载 OPEN 运营异常计数。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.112**：`WorkOrderPage.exceptionSummaries` + `WorkOrderDirectoryExceptionSummary` |
| Backend | `workorder.api` SPI + `operations` JDBC GROUP BY OPEN；PROJECT `operations.exception.read` soft-gate |
| Admin Web | 列「异常摘要」（SLA 与更新时间之间）；`待处理 N` / soft-omit 空值 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 口径：`ops_operational_exception.status = OPEN`（不含 ACKNOWLEDGED）
- 仅含 `openCount > 0` 行；有能力无 OPEN 异常返回 `[]`；缺能力省略属性
- 无筛选参数、无 Flyway、无新 capability

## 4. 明确未实现

- 异常筛选 query 参数（母版未列；可选后续）
- 完整异常 DTO 列表（目录深链已由 **M451** 进队列）
- Network Portal 变更
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
