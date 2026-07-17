---
title: ADR-034：Network Portal 指派师傅适配器复用 ManualAssign
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
---

# ADR-034：Network Portal 指派师傅适配器复用 ManualAssign

## 1. 状态与已接受决策

本 ADR 作为 M196 的边界与授权结论，正式接受：

1. Network Portal **写命令**窄切片仅接受「指派师傅」适配器；**不**新建 portal 模块、**不**新建并行派单/评分引擎；
2. HTTP（Core OpenAPI `0.88.0`）：
   - `POST /api/v1/network-portal/tasks/{taskId}:assign-technician`
3. **请求体**：`{ technicianAssigneeId, businessType }`；**禁止**客户端提交 `networkAssigneeId`；
4. **上下文**：`X-Network-Context` 必填（同 ADR-032）；服务端强制
   `networkAssigneeId = contextNetworkId`（UUID 字符串形态）；
5. **前置失败关闭**：
   - 主体对上下文网点持有 ACTIVE `NetworkMembership`，否则 `PORTAL_CONTEXT_INVALID`；
   - 师傅对本网点持有 ACTIVE `NetworkTechnicianMembership`（并在廉价可及处经
     `TechnicianEligibilityQuery` 校验可接单）；
   - 任务已有 ACTIVE NETWORK 责任且 assignee **不是**上下文网点 → 冲突失败关闭；
   - 任务已有 ACTIVE TECHNICIAN 责任且 assignee **不是**请求师傅 →
     `SERVICE_ASSIGNMENT_CONFLICT`（改派 **out of scope**）；
   - 同网点 + 同师傅 → 委托 `ManualServiceAssignmentService.manualAssign` 幂等成功；
6. **能力**：种子 `networkPortal.assignTechnician`（HIGH）。Portal 门禁按 NETWORK scope 校验该能力；
   委托 ManualAssign 时，底层 `dispatch.assignment.manage` /
   `dispatch.capacity.configure` 在同一请求内按 **NETWORK scope** 鉴权（TENANT 授权仍覆盖），
   使网点主体无需 TENANT 级派单能力即可完成本网点初派，且无法经 Admin ManualAssign 路径越权；
7. **编排归属**：`dispatch` 模块提供 Portal 写适配器；复用已 Implemented 的 ManualAssign
   激活 saga；不复制容量/激活领域逻辑；
8. Page Registry：`NETWORK.TECHNICIAN.ASSIGN`（catalog → `page-registry-v5`）；导航 pageId
   不是授权真相；
9. **不**接受：师傅改派 / 评分 / 硬过滤 / DispatchDecision、预约/资料 Network 写、离线工作包、
   ORGANIZATION SavedView、Consumer Identity。

## 2. 上下文

M194 已交付 Network Portal 只读与可信 `X-Network-Context`；M144 已交付 Admin ManualAssign。
网点需要在本网点上下文内指派师傅，但不得让客户端自报 networkId，也不得发明第二套派单引擎。

## 3. 后果

- OpenAPI 从 `0.87.0` 升至 `0.88.0`；Flyway V096 种子 `networkPortal.assignTechnician`；
- ArchitectureTest 保持 `dispatch → network::api` / `authorization::api`；
- Admin Web Network Portal 任务页增加指派表单（师傅来自 `/network-portal/technicians`）；
- 改派、评分引擎若需要，须另接受切片。
