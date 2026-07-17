---
title: ADR-033：Technician Portal Feed 与可信师傅上下文
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Technician Portal Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-026-portal-context-navigation.md
  - decisions/ADR-032-network-portal-read-apis.md
---

# ADR-033：Technician Portal Feed 与可信师傅上下文

## 1. 状态与已接受决策

本 ADR 作为 M195 的边界与授权结论，正式接受：

1. Technician Portal Feed 只读查询由 `readmodel` 编排 fan-in；**不**新建 portal 模块；
2. HTTP（Core OpenAPI `0.87.0`）：
   - `GET /api/v1/technician/me/task-feed`（可选 `sinceCursor`；含 tombstone）
   - `GET /api/v1/technician/me/schedule`
   - `GET /api/v1/technician/me/sync-summary`
3. **明确推迟**：`GET /api/v1/mobile-work-packages/{id}/status`（离线工作包 runtime 未 Accepted）；
4. **上下文**：`X-Technician-Context` 必填；接受 M188 `TECHNICIAN|NETWORK|{uuid}` 或经 ACTIVE
   NetworkTechnicianMembership 校验后的纯 network UUID；**禁止** query-param `networkId`；
5. 上下文缺失/伪造/非师傅成员/无 TechnicianProfile → `PORTAL_CONTEXT_INVALID`（403）；
6. **能力**：不新增 `technicianPortal.read` 种子。要求 ACTIVE TechnicianProfile +
   ACTIVE NetworkTechnicianMembership + NETWORK scope 既有能力 `task.readAssigned`；
   缺能力 `ACCESS_DENIED`；跨网点失败关闭；
7. **数据**：
   - Feed：当前师傅 assignee（`principalId` 或 `technicianProfileId`）的 ACTIVE TECHNICIAN
     `ServiceAssignment`，以及同网点收敛的 ACTIVE RESPONSIBLE `TaskAssignment`；
   - 增量 `sinceCursor` 后包含 ENDED ServiceAssignment / REVOKED TaskAssignment tombstone
     （仅 `taskId` + `invalidationReason`，无敏感正文）；
   - Schedule：对 ACTIVE 责任任务 fan-in 预约（非敏感字段）；
   - sync-summary：pending feed / 预约窗口 / tombstone 轻量计数（无离线命令 runtime）；
8. Page Registry：`TECHNICIAN.TASK.LIST` / `TECHNICIAN.SCHEDULE` / `TECHNICIAN.SYNC.SUMMARY`
   （catalog → `page-registry-v4`）；导航 pageId 不是授权真相；
9. **不**接受离线工作包、Network Portal 写命令、完整 Technician App（GPS/相机/大上传）、
   Consumer Identity、评分或 BUSINESS SLA。

## 2. 上下文

M188 已提供 TECHNICIAN contexts；M194 交付 Network Portal 只读；dispatch 已有 TECHNICIAN
ServiceAssignment。师傅端需要最小可靠 Feed/日程/同步摘要面，但不得让客户端自报 networkId
扩权，也不得用导航 pageId 代替业务鉴权。

## 3. 后果

- ArchitectureTest 验证 `readmodel → dispatch::api` / `task::api` / `appointment::api` /
  `network::api` / `authorization::api`；
- Admin Web 升级 `/technician-portal/*` shell，请求携带 `X-Technician-Context`；
- 离线工作包、移动同步命令若需要，须另接受切片。
