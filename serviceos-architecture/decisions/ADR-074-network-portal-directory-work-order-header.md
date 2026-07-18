---
title: ADR-074 Network Portal 目录页工单头字段（服务产品 / 区域 / 接收时间）
status: Accepted
date: 2026-07-17
milestone: M236
---

# ADR-074：Network Portal 目录页工单头字段（服务产品 / 区域 / 接收时间）

- Status: Accepted
- Relates to: product/03 §5 默认列「服务产品」「区域」「更新时间」、Admin `WorkOrder` 非 PII 子集、architecture/249、testing/233、M236

## Context

M230～M235 已闭合目录旁载 enrichment（师傅/预约/联系/资料/整改/SLA）。
product/03 §5 仍要求「服务产品」「区域」「更新时间」。这些字段已在 Admin `WorkOrder` /
`WorkOrderView` 以非 PII 形式 Accepted；Network Portal 目录项此前仅有 assignment 形字段。

## Decision

1. 扩展 `NetworkPortalWorkOrderItem` / `NetworkPortalTaskItem`：
   `brandCode`、`serviceProductCode`、`provinceCode`、`cityCode`、`districtCode`、`receivedAt`。
2. 数据源：新只读端口 `WorkOrderDirectoryHeaderQuery`（`wo_work_order` 子集；不含客户 PII）。
3. 门禁：列表既有 NETWORK `networkTask.read`；仅对本页 ACTIVE 责任 workOrderIds fan-in；缺失为 null。
4. 「更新时间」MVP 映射为 Admin 对齐的 `receivedAt`（无独立 updatedAt 列）。
5. OpenAPI `1.0.15 → 1.0.16`；catalog v16；无 Flyway；无新 pageId。
6. **明确未实现**：用户脱敏 PII、独立 updatedAt、目录 reviews、notifications、Portal ACK。

## Consequences

- product/03 目录非 PII 默认列可闭合。
- 不引入 `workOrder.read` PROJECT 能力要求。
