---
title: ADR-032：Network Portal 只读查询与可信网点上下文
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation.md
  - decisions/ADR-031-admin-recent-resources.md
---

# ADR-032：Network Portal 只读查询与可信网点上下文

## 1. 状态与已接受决策

本 ADR 作为 M194 的边界与授权结论，正式接受：

1. Network Portal 只读查询由 `readmodel` 编排 fan-in；**不**新建 portal 模块；
2. HTTP（Core OpenAPI `0.86.0`）：
   - `GET /api/v1/network-portal/work-orders`
   - `GET /api/v1/network-portal/tasks`
   - `GET /api/v1/network-portal/technicians`
   - `GET /api/v1/network-portal/workbench`（计数/摘要）
   - `GET /api/v1/network-portal/capacity`（`dsp_capacity_counter` 按 networkId）
3. **上下文**：`X-Network-Context` 必填；接受 M188 `NETWORK|NETWORK|{uuid}` 或经 ACTIVE
   NetworkMembership 校验后的纯 network UUID；**禁止** query-param `networkId`；
4. 上下文缺失/伪造/非成员 → `PORTAL_CONTEXT_INVALID`（403）；
5. **能力**：不新增 `networkPortal.read` 种子。要求 ACTIVE NetworkMembership +
   NETWORK scope 既有能力：`networkTask.read`（工单/任务/工作台/容量）、
   `technician.readOwnNetwork`（师傅列表）；缺能力 `ACCESS_DENIED`；跨网点失败关闭；
6. **数据**：工单/任务来自该网点 ACTIVE NETWORK `ServiceAssignment`（dispatch 公开只读端口）；
   师傅来自该网点 ACTIVE `NetworkTechnicianMembership`（network 公开只读端口）；
7. Page Registry 增补 `NETWORK.WORKORDER.LIST`（catalog → `page-registry-v3`）；
8. **不**接受 Technician Feed §11、Admin work-queues、Network 写命令、完整 product/03。

## 2. 上下文

M188 已提供 NETWORK contexts 与导航 stubs；M185 提供网点目录与师傅关系；dispatch 已有
ACTIVE ServiceAssignment。运营需要最小可靠 Network Portal 只读面，但不得让客户端自报
networkId 扩权，也不得用导航 pageId 代替业务鉴权。

## 3. 后果

- ArchitectureTest 验证 `readmodel → dispatch::api` / `network::api` / `authorization::api`；
- Admin Web 升级 `/network-portal/*` shell，请求携带 `X-Network-Context`；
- Technician App、离线、评分/容量策略引擎、写命令若需要，须另接受切片。
