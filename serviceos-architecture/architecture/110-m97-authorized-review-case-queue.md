---
title: M97 授权审核案例队列
status: Implemented
milestone: M97
---

# M97 授权审核案例队列

## 1. 目标

窄接受 API-06 §6 `GET /review-cases`，建立首个跨工单、实时项目范围约束的审核队列。
本里程碑不是通用 `work-queues` 或 SavedView 平台。

## 2. 查询语义

- 筛选：projectId、status（默认 OPEN）、origin、taskId；
- 排序：`createdAt ASC, reviewCaseId ASC`，保证待审核 FIFO；
- limit 1～100；游标绑定 scopeDigest、project/status/origin/task 全部条件；
- projectId 缺省时通过 `ProjectScopeAuthorizationService` 解析 TENANT/PROJECT/REGION/NETWORK
  实时项目集合并执行单条范围化 SQL；
- 能力为 `evidence.review`；详情 `evidence.read` 语义不变。

## 3. 安全响应

队列返回 Case 身份、状态、CLIENT/重开血缘与最新决定代码/原因码。禁止 snapshot digest、
createdBy、Decision note/approvalRef/decidedBy 与任何正文。

## 4. 契约与数据库

- Core OpenAPI **0.67.0**；
- Flyway **V081** 增加 `(tenant_id,status,created_at,review_case_id)` 稳定游标索引；
- 当前 Flyway V081 / 83 migrations。

## 5. 明确未实现

通用 work-queues/SavedView、SLA/assignee/target/reason 过滤、workOrder 展开、
Correction/Outbound 队列、FACTS_CALCULATIONS、Portal。
