---
title: M194 Network Portal 只读查询
status: Implemented
milestone: M194
lastUpdated: 2026-07-17
relatedMilestones: [M185, M188, M193]
---

# M194 Network Portal 只读查询

## 目标

在 M188 Portal 上下文与 M185 网点目录之上，交付 Network Portal 最小可靠只读纵向切片：
按可信 `X-Network-Context` 列出本网点 ACTIVE 责任工单/任务、师傅与容量摘要，失败关闭跨网点。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §10 Network 查询子集（work-orders / tasks / technicians / workbench / capacity）；
  - ADR-032：归属 `readmodel` fan-in；dispatch/network 公开只读端口；
  - Core OpenAPI `0.86.0`；
  - Page Registry `NETWORK.WORKORDER.LIST` + catalog `page-registry-v3`；
  - Admin Web `/network-portal/*` shell 与只读列表页；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - Technician Feed §11 / 离线工作包；
  - Network Portal 写命令（指派师傅等）；
  - 评分/容量策略引擎、ORGANIZATION SavedView、Consumer Identity；
  - 完整 product/03 设计系统。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §10（M194 Accepted）
- ADR-032
- M188 Portal 上下文；M185 网点/师傅目录

## 设计要点

- `X-Network-Context` = `NETWORK|NETWORK|{uuid}` 或经 ACTIVE NetworkMembership 校验的 UUID；
- 禁止 query-param networkId；伪造/非成员 → `PORTAL_CONTEXT_INVALID`；
- 能力：`networkTask.read` / `technician.readOwnNetwork`（NETWORK scope），不新增 capability 种子；
- 工单/任务：ACTIVE NETWORK ServiceAssignment；师傅：ACTIVE NetworkTechnicianMembership；
- capacity：`dsp_capacity_counter` 按 network assignee 聚合。

## 已实现

- [x] ADR-032
- [x] OpenAPI Core `0.86.0`
- [x] `NetworkPortalQueryService` / Controller
- [x] dispatch `NetworkActiveAssignmentQuery` / `NetworkCapacitySummaryQuery`
- [x] network `NetworkPortalTechnicianQuery`
- [x] Admin Web Network Portal shell + 列表页
- [x] PostgresIT / Security MVC / ArchitectureTest / E2E spec

## 明确未实现

- Technician Feed / 离线；
- Network 写命令与完整产品 UI；
- Admin work-queues §4。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.86.0
- IT：`NetworkPortalReadPostgresIT`
- MVC：`NetworkPortalControllerSecurityTest`
- E2E：`network-portal-read.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalReadPostgresIT,NetworkPortalControllerSecurityTest
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
