---
title: M200 Network Portal 改派师傅
status: Implemented
milestone: M200
lastUpdated: 2026-07-17
relatedMilestones: [M25, M144, M196]
---

# M200 Network Portal 改派师傅

## 目标

在 M196 Network Portal 初派之上，交付同网点师傅改派写切片：按可信 `X-Network-Context`
将本网点任务的 ACTIVE TECHNICIAN 责任从师傅 A 切换到师傅 B，失败关闭跨网点与伪造上下文。

## 范围与非目标

- 范围：
  - `POST /api/v1/network-portal/tasks/{taskId}:reassign-technician`；
  - ADR-038：适配器委托 `ManualServiceAssignmentService.reassignTechnician`；
  - Core OpenAPI `0.92.0`；Flyway V098 种子 `networkPortal.reassignTechnician`；
  - Page Registry catalog `page-registry-v7`；
  - Admin Web Network Portal 改派动作；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - 跨网点 NETWORK 改派；
  - 资料补传 / onBehalfOf、Visit；
  - 评分/硬过滤、离线工作包回收 UI；
  - ORGANIZATION SavedView、Consumer Identity。

## 事实源

- ADR-038 / ADR-034
- M144 ManualAssign；M24/M25 ServiceAssignment 改派路径；M196 Portal 初派

## 设计要点

- Body：`technicianAssigneeId` + `businessType` + `reasonCode`；服务端强制
  `networkAssigneeId = contextNetworkId`，并解析当前 ACTIVE TECHNICIAN 作为 supersedes；
- 鉴权顺序：解析上下文 → ACTIVE NetworkMembership → NETWORK
  `networkPortal.reassignTechnician` → 师傅 ACTIVE 关系（+ 可接单）→ 本网点 ACTIVE NETWORK →
  存在不同 ACTIVE TECHNICIAN → 委托 ManualReassign；
- 同幂等键重放成功；无 ACTIVE TECHNICIAN 时拒绝并提示使用 assign。

## 已实现

- [x] ADR-038
- [x] OpenAPI Core `0.92.0`
- [x] Flyway V098（098/100）
- [x] `ManualServiceAssignmentService.reassignTechnician`
- [x] `NetworkPortalReassignTechnicianService` / Controller
- [x] Admin Web 改派 + E2E spec
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 资料 Network 写 / onBehalfOf；
- 完整 Network Portal 产品 UI / 设计系统；
- 离线工作包。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.92.0
- Flyway：`authorization/V098__seed_network_portal_reassign_technician_capability.sql`
- IT：`NetworkPortalReassignTechnicianPostgresIT`
- MVC：`NetworkPortalReassignTechnicianControllerSecurityTest`
- E2E：`network-portal-reassign-technician.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalAssignTechnicianPostgresIT,NetworkPortalReassignTechnicianPostgresIT,NetworkPortalReassignTechnicianControllerSecurityTest,DispatchServiceAssignmentPostgresIT
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
