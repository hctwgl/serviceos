---
title: ADR-060：Network Portal 工作区 Visit/表单提交摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
  - Fieldwork Owner
  - Forms Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-059-network-portal-workspace-sla-summary.md
---

# ADR-060：Network Portal 工作区 Visit/表单提交摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M222 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin `GET /work-orders/{id}/workspace` 或 section APIs；
2. 新增可选数组字段（缺对应 NETWORK 能力时 **省略**，不得用空数组伪装无权限）：
   - `visits`：元素字段集与已 Accepted 的 `WorkOrderWorkspaceVisitSummary` 完全一致
     （无 GPS/note/device/operationRefs/evidenceRefs）；
     soft-gate NETWORK `visit.read`；仅含 `networkId` = 可信上下文网点且
     `taskId ∈` 工作区 ACTIVE `taskIds` 的 Visit；
   - `formSubmissions`：元素字段集与已 Accepted 的
     `WorkOrderWorkspaceFormSubmissionSummary` 完全一致（无 values/校验正文/submittedBy）；
     soft-gate NETWORK `form.read`；仅含工作区 ACTIVE `taskIds` 上的提交摘要；
3. 基座门禁不变：`X-Network-Context` + ACTIVE membership + NETWORK `networkTask.read`
   + ACTIVE NETWORK ServiceAssignment；Visit/表单为 soft-gate enrichment；
4. 领域侧新增 NETWORK 作用域查询端口（`VisitService.listByWorkOrderOnNetwork`、
   `FormSubmissionQueryService.listForTaskOnNetwork`），鉴权使用
   `networkCapability(visit.read|form.read)`；OpenAPI **1.0.1 → 1.0.2**；
5. **不**新增 Flyway、**不**新增 pageId（catalog 仍 `page-registry-v16`）；
6. **不**接受：表单 definition/values、Evidence 槽位/资料项摘要、工作台 SLA 风险计数、
   Admin workspace 复用、客户 PII、独立 `/network-portal/.../visits|forms` 列表 API、
   Visit/表单写命令、Portal ACK、notifications。

## 2. 上下文

product/03 §6.1 要求工作区展示本网点相关 Visit 与表单提交摘要；ADR-051～059 在无字段
切片时禁止发明 DTO。Admin M88/M95 已 Accepted 非 PII 摘要字段与领域查询端口；本 ADR
将其窄投影到 NP workspace，闭合 status §5 候选 #1。

## 3. 后果

- Core OpenAPI 1.0.2 + 后端编排 + Admin Web 展示 + IT/E2E；
- Evidence 摘要、独立 NP Visit/表单读 API 仍须另接受。
