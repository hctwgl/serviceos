---
title: M196 Network Portal 指派师傅
status: Implemented
milestone: M196
lastUpdated: 2026-07-17
relatedMilestones: [M144, M185, M188, M194]
---

# M196 Network Portal 指派师傅

## 目标

在 M194 Network Portal 只读与 M144 ManualAssign 之上，交付 Network Portal 最小可靠写切片：
按可信 `X-Network-Context` 为本网点任务指派师傅，强制 `networkAssigneeId`，失败关闭跨网点与改派。

## 范围与非目标

- 范围：
  - `POST /api/v1/network-portal/tasks/{taskId}:assign-technician`；
  - ADR-034：dispatch Portal 适配器委托 `ManualServiceAssignmentService.manualAssign`；
  - Core OpenAPI `0.88.0`；Flyway V096 种子 `networkPortal.assignTechnician`；
  - Page Registry `NETWORK.TECHNICIAN.ASSIGN` + catalog `page-registry-v5`；
  - Admin Web Network Portal 任务列表指派表单；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - 师傅改派 / 离线工作包失效 UI（超出 ManualAssign 既有领域效果）；
  - 评分 / 硬过滤 / DispatchDecision；
  - 预约 / 资料 Network 写；
  - ORGANIZATION SavedView、Consumer Identity、完整 product/03。

## 事实源

- ADR-034
- M144 ManualAssign；M194 Network Portal 只读；M185 网点/师傅目录
- product/03 `NETWORK.TECHNICIAN.ASSIGN` 仅作 pageId/命名指导（产品能力码仍为 Proposed）

## 设计要点

- Body 仅 `technicianAssigneeId` + `businessType`；服务端强制
  `networkAssigneeId = contextNetworkId`；
- 鉴权顺序：解析上下文 → ACTIVE NetworkMembership → NETWORK
  `networkPortal.assignTechnician` → 师傅 ACTIVE 关系（+ 可接单）→ 冲突预检 → ManualAssign；
- 委托期间底层派单/容量按 NETWORK scope 鉴权，避免要求 TENANT 级派单能力；
- 同网点同师傅幂等成功；不同师傅 ACTIVE → `SERVICE_ASSIGNMENT_CONFLICT`。

## 已实现

- [x] ADR-034
- [x] OpenAPI Core `0.88.0`
- [x] Flyway V096 `networkPortal.assignTechnician`
- [x] `NetworkPortalAssignTechnicianService` / Controller
- [x] Page Registry `NETWORK.TECHNICIAN.ASSIGN`
- [x] Admin Web 指派表单 + E2E spec
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 改派、评分引擎、预约/资料 Network 写；
- 完整 Network Portal 产品 UI / 设计系统；
- 离线工作包。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.88.0
- Flyway：`authorization/V096__seed_network_portal_assign_technician_capability.sql`
- IT：`NetworkPortalAssignTechnicianPostgresIT`
- MVC：`NetworkPortalAssignTechnicianControllerSecurityTest`
- E2E：`network-portal-assign-technician.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalAssignTechnicianPostgresIT,NetworkPortalAssignTechnicianControllerSecurityTest,NetworkPortalReadPostgresIT,ManualServiceAssignmentPostgresIT
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
