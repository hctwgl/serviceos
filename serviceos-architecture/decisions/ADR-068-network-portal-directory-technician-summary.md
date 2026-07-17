---
title: ADR-068：Network Portal 目录页师傅服务端摘要字段
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-055-network-portal-directory-technician-fanin.md
  - decisions/ADR-066-network-portal-workspace-technician-summary.md
---

# ADR-068：Network Portal 目录页师傅服务端摘要字段

## 1. 状态与已接受决策

本 ADR 作为 M230 的边界结论，正式接受：

1. 扩展既有路径
   `GET /api/v1/network-portal/work-orders` 与
   `GET /api/v1/network-portal/tasks` 的列表页包装，**不**新增独立路径；
2. 在 `NetworkPortalWorkOrderPage` / `NetworkPortalTaskPage` 新增可选数组字段
   `technicians`：元素字段集与已 Accepted 的 `NetworkPortalTechnicianItem` 完全一致；
3. soft-gate：NETWORK `technician.readOwnNetwork`（与 M194/M217/M228 一致）；
   缺能力时省略属性（不得用空数组伪装无权限）；有能力时可为空列表；
4. 范围：仅包含本页 `items[].technicianId` 命中的本网点 ACTIVE 师傅
   （`technicianProfileId` 字符串匹配）；
5. 编排复用已 Implemented 的 `NetworkPortalTechnicianQuery.listActiveTechnicians`；
6. OpenAPI **1.0.9 → 1.0.10**；**不**新增 Flyway、**不**新增 pageId（catalog 仍
   `page-registry-v16`）；
7. Admin Web 以服务端 `technicians` 替换 M217 工单/任务目录客户端 fan-in；
   任务页指派下拉仍可消费既有 `GET /technicians`（写控件候选全集）；
8. **不**接受：客户 PII、写控件字段发明、Admin workspace 复用、Portal ACK、
   notifications、新 pageId、列表预约 N+1、产能申请。

## 2. 上下文

product/03 §5 要求目录展示师傅；M217 仅客户端 fan-in。M228 已在工作区证明
`technicians[]` soft-gate 与 `NetworkPortalTechnicianItem` 复用安全。本 ADR 将该投影
窄接到工单/任务目录页包装，闭合 M215→M227 / M216→M228 同型的目录侧替换。

## 3. 后果

- Core OpenAPI 1.0.10 + 后端编排 + Admin Web + IT/E2E；
- notifications / Portal ACK 仍 deferred。
