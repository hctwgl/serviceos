---
title: ADR-061：Network Portal 工作区 Evidence 槽位/资料项摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Evidence Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-060-network-portal-workspace-visit-form-summary.md
---

# ADR-061：Network Portal 工作区 Evidence 槽位/资料项摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M223 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin `GET /work-orders/{id}/workspace` 或 section APIs；
2. 新增可选数组字段（缺 NETWORK `evidence.read` 时 **同时省略**，不得用空数组伪装无权限）：
   - `evidenceSlots`：元素字段集与已 Accepted 的 `WorkOrderWorkspaceEvidenceSlotSummary`
     完全一致（无 requirementDefinition/resolutionExplanation JSON）；
   - `evidenceItems`：元素字段集与已 Accepted 的 `WorkOrderWorkspaceEvidenceItemSummary`
     完全一致（仅 revision 计数/最新状态，无 Revision 图、file、captureMetadata）；
3. soft-gate：NETWORK `evidence.read`；有能力时两数组均出现（无数据则为 `[]`）；
   范围仅 ACTIVE NETWORK assignment 的 `taskIds`；单任务未完成可靠解析时，
   NETWORK 作用域查询端口返回空列表（不抛 `TASK_STATE_CONFLICT`），不得把工作区
   顶层打成冲突或污染只读事务；
4. 领域侧新增 NETWORK 作用域查询端口
   （`EvidenceSlotQueryService.listForTaskOnNetwork`、
   `EvidenceItemQueryService.listSummariesForTaskOnNetwork`），鉴权使用
   `networkCapability(evidence.read)`；OpenAPI **1.0.2 → 1.0.3**；
5. **不**新增 Flyway、**不**新增 pageId（catalog 仍 `page-registry-v16`）；
6. **不**接受：Admin workspace 复用、独立 NP Evidence 列表 API、缩略图/下载、
   Revision 全文、definition JSON、工作台 SLA 风险计数、notifications、Portal ACK、
   onBehalf 写控件嵌入工作区。

## 2. 上下文

product/03 §6.1 要求工作区展示本网点相关「资料」摘要；ADR-051～060 在无字段切片时禁止
发明 DTO。Admin M89/M95 已 Accepted 非 PII 槽位/资料项摘要与领域查询端口；本 ADR 将其
窄投影到 NP workspace，闭合 status §5 候选 #1。

## 3. 后果

- Core OpenAPI 1.0.3 + 后端编排 + Admin Web 展示 + IT/E2E；
- 独立 NP Evidence 读列表、缩略图/下载仍须另接受。
