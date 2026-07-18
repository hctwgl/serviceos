---
title: ADR-047：Network Portal 整改详情只读 UI
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-040-network-portal-correction-queue.md
  - decisions/ADR-039-network-portal-evidence-on-behalf.md
  - decisions/ADR-046-network-portal-capacity-page.md
---

# ADR-047：Network Portal 整改详情只读 UI

## 1. 状态与已接受决策

本 ADR 作为 M209 的边界结论，正式接受：

1. 在 M202 已 Accepted 的
   `GET /api/v1/network-portal/correction-cases/{correctionCaseId}` 之上，交付 Admin Web
   `/network-portal/corrections/:id` **只读详情页**；
2. **不**新建 HTTP 路径、**不**新建 capability、**不**新增 Flyway、**不**新增 Page Registry
   pageId（仍归属 `NETWORK.CORRECTION.QUEUE`；catalog **保持** `page-registry-v15`）；
3. 响应契约复用既有 Core OpenAPI `CorrectionCase`（含 `sourceEvidenceSetSnapshotId`、
   `sourceSnapshotContentDigest`、`createdBy`、`closed*`/`waived*`、`resubmissions[]`）；
4. 列表页整改案例 ID 深链至详情；详情提供任务代补深链（`/network-portal/tasks?taskId=`）；
5. Core OpenAPI **保持 `0.99.0`**；Flyway **仍 100/102**；
6. **不**接受：Portal close/waive/ACK、Admin 整改写命令伪装、新 pageId、通知、产能写。

## 2. 上下文

M202 已交付 list/get HTTP 与队列壳；Admin Web 仅消费 list。get 返回比 list 摘要更丰富的
`CorrectionCase`，属 Accepted 读契约上的 UI enrichment 缺口。

## 3. 后果

- Admin Web 详情页 + 列表深链 + E2E；
- close/waive 等写面若需 Portal 化，须另接受切片。
