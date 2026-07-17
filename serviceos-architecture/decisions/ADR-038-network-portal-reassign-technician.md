---
title: ADR-038：Network Portal 改派师傅适配器复用 ServiceAssignment 改派路径
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation-in-authorization.md
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-034-network-portal-assign-technician.md
---

# ADR-038：Network Portal 改派师傅适配器复用 ServiceAssignment 改派路径

## 1. 状态与已接受决策

本 ADR 作为 M200 的边界与授权结论，正式接受：

1. Network Portal **写命令**扩展「同网点改派师傅」；**不**新建 portal 模块、**不**新建评分/硬过滤引擎；
2. HTTP（Core OpenAPI `0.92.0`）：
   - `POST /api/v1/network-portal/tasks/{taskId}:reassign-technician`
3. **请求体**：`{ technicianAssigneeId, businessType, reasonCode }`；**禁止**客户端提交
   `networkAssigneeId` / `supersedesServiceAssignmentId`；
4. **上下文与门禁**：复用 ADR-034——`X-Network-Context`、ACTIVE `NetworkMembership`、
   目标师傅 ACTIVE 网点关系 + 可接单；任务 ACTIVE NETWORK 责任必须等于上下文网点；
5. **改派前置**：
   - 必须已存在 ACTIVE TECHNICIAN 责任且 assignee **不等于**目标师傅，否则
     `VALIDATION_FAILED`（无师傅时走 M196 `:assign-technician`）；
   - 同网点同目标师傅 → 幂等回放当前 ACTIVE 回执；
   - **不**支持跨网点 NETWORK 改派；
6. **能力**：种子 `networkPortal.reassignTechnician`（HIGH）。Portal 门禁按 NETWORK scope 校验；
   委托期间底层 `dispatch.assignment.manage` / `dispatch.capacity.configure` 按 NETWORK scope
   （与 M196 `NetworkScopedDispatchAuthorization` 一致）；
7. **编排归属**：`dispatch` 模块提供 Portal 适配器 + `ManualServiceAssignmentService.reassignTechnician`；
   复用已 Implemented 的 ServiceAssignment prepare（`supersedes` + `reasonCode`）→
   confirmTaskPrepared → activate → complete 路径；不发明并行 saga；
8. Page Registry：扩展 `NETWORK.TECHNICIAN.ASSIGN` 能力列表含 `networkPortal.reassignTechnician`
   （catalog → `page-registry-v7`）；导航 pageId 不是授权真相；
9. **不**接受：资料补传 / `evidence.submitOnBehalf`、跨网点改派、评分/硬过滤、离线工作包回收 UI、
   ORGANIZATION SavedView、Consumer Identity。

## 2. 上下文

M196 已交付 Network Portal 初派并明确将「不同师傅 ACTIVE」冲突留给后续切片。Admin/领域侧
ServiceAssignment 改派路径（prepare with supersedes）已 Implemented。网点需要在本网点上下文内
将师傅从 A 改到 B，失败关闭跨网点与伪造上下文。

## 3. 后果

- OpenAPI 从 `0.91.0` 升至 `0.92.0`；Flyway V098 种子 `networkPortal.reassignTechnician`（098/100）；
- ArchitectureTest：dispatch 边界不变；
- Admin Web Network Portal 任务页增加改派动作；
- 资料 Network 写若需要，须另接受切片。
