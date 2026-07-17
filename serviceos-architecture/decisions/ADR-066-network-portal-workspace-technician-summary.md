---
title: ADR-066：Network Portal 工作区当前师傅服务端摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-051-network-portal-work-order-workspace.md
  - decisions/ADR-054-network-portal-workspace-technician-fanin.md
  - decisions/ADR-065-network-portal-workspace-appointment-contact-summary.md
---

# ADR-066：Network Portal 工作区当前师傅服务端摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M228 的边界结论，正式接受：

1. 扩展既有路径 `GET /api/v1/network-portal/work-orders/{workOrderId}/workspace`，
   **不**复用 Admin workspace；
2. 新增可选数组字段 `technicians`：元素字段集与已 Accepted 的
   `NetworkPortalTechnicianItem` 完全一致；
3. soft-gate：NETWORK `technician.readOwnNetwork`（与 M194/M216 一致）；
   缺能力时省略属性（不得用空数组伪装无权限）；有能力时可为空列表；
4. 范围：仅包含工作区头/`tasks[].technicianId` 命中的本网点 ACTIVE 师傅
   （`technicianProfileId` 匹配）；未指派任务仍由 UI 从 `tasks` 推导深链
   （不新增字段）；
5. 编排复用已 Implemented 的 `NetworkPortalTechnicianQuery.listActiveTechnicians`；
6. OpenAPI **1.0.7 → 1.0.8**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
7. Admin Web 以服务端 `technicians` 替换 M216 客户端 fan-in；保留既有 testid 与深链；
8. **不**接受：客户 PII、写控件、Admin workspace 复用、Portal ACK、notifications、
   新 pageId、发明更宽师傅详情字段。

## 2. 上下文

product/03 §6.1 要求工作区展示当前师傅；M216 仅客户端 fan-in。M194
`NetworkPortalTechnicianItem` 已 Accepted。本 ADR 将该投影窄接到 NP workspace。

## 3. 后果

- Core OpenAPI 1.0.8 + 后端编排 + Admin Web 展示 + IT/E2E；
- notifications / Portal ACK 仍 deferred。
