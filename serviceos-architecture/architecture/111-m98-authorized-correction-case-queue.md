---
title: M98 授权整改案例队列
status: Implemented
milestone: M98
---

# M98 授权整改案例队列

## 1. 目标

窄接受 API-06 §6 `GET /correction-cases`，建立跨工单、实时项目范围约束的整改跟踪队列。
本里程碑不是通用 `work-queues` 或 SavedView 平台。

## 2. 查询语义

- 筛选：projectId、status（默认 OPEN）、taskId、sourceReviewCaseId；
- 排序：`createdAt ASC, correctionCaseId ASC`，保证跟踪 FIFO；
- limit 1～100；游标绑定 scopeDigest、project/status/task/sourceReview 全部条件；
- projectId 缺省时通过 `ProjectScopeAuthorizationService` 解析 TENANT/PROJECT/REGION/NETWORK
  实时项目集合并执行单条范围化 SQL；
- 能力为 `evidence.read`（与 CorrectionCase 详情/按 Task 列表一致；不新增 `correction.read`）。

说明：M47 起驳回后同事务链接整改 Task 会把多数案例推进到 `IN_PROGRESS`；运营跟踪通常显式传
`status=IN_PROGRESS`/`RESUBMITTED`。默认 OPEN 仅表示未显式筛选时的稳定缺省，不静默合并多状态。

## 3. 安全响应

队列返回 Case 身份、来源审核引用、原因码、整改 Task、状态时间戳与补传次数。禁止
sourceSnapshotContentDigest、createdBy/closedBy/waivedBy、waiveApprovalRef/waiveNote 与任何正文。

## 4. 契约与数据库

- Core OpenAPI **0.68.0**；
- Flyway **V082** 增加 `(tenant_id,status,created_at,correction_case_id)` 稳定游标索引；
- 当前 Flyway V082 / 84 migrations。

## 5. 明确未实现

通用 work-queues/SavedView、SLA/assignee enrich、Outbound 队列、异常队列 Project Scope 硬化、
FACTS_CALCULATIONS、Portal。
