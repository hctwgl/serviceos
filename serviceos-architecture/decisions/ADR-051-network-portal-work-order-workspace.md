---
title: ADR-051：Network Portal 限定工单工作区只读适配
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-046-network-portal-capacity-page.md
  - decisions/ADR-050-network-portal-membership-detail.md
---

# ADR-051：Network Portal 限定工单工作区只读适配

## 1. 状态与已接受决策

本 ADR 作为 M213 的边界与授权结论，正式接受：

1. Network Portal **新增**只读路径（**不**复用 Admin `GET /work-orders/{id}/workspace`）：
   `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`；
2. **上下文**：`X-Network-Context` 必填；**禁止** query-param `networkId`；
3. **能力**：不新增 capability 种子；要求 ACTIVE NetworkMembership + NETWORK scope
   `networkTask.read`（映射 product/03 `workOrder.readAssigned`）；
4. **数据门禁**：`workOrderId` 必须对本网点存在 ≥1 条 ACTIVE NETWORK `ServiceAssignment`，
   否则 `ACCESS_DENIED`（改派后旧 URL 失败关闭）；
5. **响应**：薄 DTO `NetworkPortalWorkOrderWorkspace`——工单头（与 list item 同形安全字段）
   + 本网点 ACTIVE 任务摘要列表（复用 `NetworkPortalTaskItem` 字段）+ `asOf`；
   **不**嵌入 Admin workspace sections、INTEGRATION、定价、其他网点历史、客户 PII；
6. Page Registry：`NETWORK.WORKORDER.WORKSPACE`（catalog → `page-registry-v16`）；
7. Core OpenAPI **`0.99.0` → `1.0.0`**；Flyway **仍 100/102**；
8. Admin Web：`/network-portal/work-orders/:id` 只读壳 + 列表深链；任务深链既有任务页；
9. **不**接受：Option A（前端直调 Admin workspace）、Portal ACK、notifications、
   FieldOperation、SLA/Visit/表单完整区块发明、新 capability。

## 2. 上下文

product/03 定义 `NETWORK.WORK_ORDER.WORKSPACE`；M194 仅有工单 list。Admin workspace
使用 PROJECT `workOrder.read`，不能安全暴露给 Network Portal。status §5 要求先窄接受契约。

## 3. 后果

- OpenAPI 升至 `1.0.0`；catalog `page-registry-v16`；
- 后续 SLA/Visit/表单/allowed-actions 编排若需要，须另接受切片。
