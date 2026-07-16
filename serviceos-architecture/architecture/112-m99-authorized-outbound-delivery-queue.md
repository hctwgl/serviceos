---
title: M99 授权外发交付队列
status: Implemented
milestone: M99
---

# M99 授权外发交付队列

## 1. 目标

窄接受 API-06 §6 `GET /outbound-deliveries`，建立跨工单、实时项目范围约束的外发交付队列。
本里程碑不是通用 `work-queues` 或 SavedView 平台；既有按 ID / 按工单查询保持不变。

## 2. 查询语义

- 筛选：projectId、status（默认 UNKNOWN）、businessMessageType、sourceWorkOrderId、sourceReviewCaseId；
- 排序：`createdAt ASC, deliveryId ASC`，保证运营 FIFO；
- limit 1～100；游标绑定 scopeDigest 与全部筛选条件；
- projectId 缺省时通过 `ProjectScopeAuthorizationService` 解析 TENANT/PROJECT/REGION/NETWORK
  实时项目集合并执行单条范围化 SQL；
- 能力为 `integration.readOutbound`。

说明：默认 UNKNOWN 对齐全人工接管关注面；其他状态需显式传 `status`，不静默合并多状态。

## 3. 安全响应

队列返回 Delivery 身份、来源引用、外部订单号、状态时间戳与 attemptCount。禁止
snapshot/payload digest、对象存储引用、操作者、externalIdempotencyKey、重放 reason/approvalRef
以及 attempt/ack/replay 明细。

## 4. 契约与数据库

- Core OpenAPI **0.69.0**；
- Flyway **V083** 增加 `(tenant_id,status,created_at,delivery_id)` 稳定游标索引；
- 当前 Flyway V083 / 85 migrations。

## 5. 明确未实现

通用 work-queues/SavedView、异常队列 Project Scope 硬化、人工标记已送达/放弃、
FACTS_CALCULATIONS、Portal。
