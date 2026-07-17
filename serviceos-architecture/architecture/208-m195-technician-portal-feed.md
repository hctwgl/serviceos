---
title: M195 Technician Portal Feed
status: Implemented
milestone: M195
lastUpdated: 2026-07-17
relatedMilestones: [M185, M188, M194]
---

# M195 Technician Portal Feed

## 目标

在 M188 Portal 上下文与 M194 Network Portal 模式之上，交付 Technician Portal 最小可靠只读
Feed 切片：按可信 `X-Technician-Context` 返回当前师傅 ACTIVE 责任、日程与同步摘要，失败关闭
跨网点与伪造上下文。

## 范围与非目标

- 范围：
  - 窄接受 API-06 §11 Technician Feed 子集（task-feed / schedule / sync-summary）；
  - ADR-033：归属 `readmodel` fan-in；dispatch/task/appointment 公开只读端口；
  - Core OpenAPI `0.87.0`；
  - Page Registry `TECHNICIAN.SYNC.SUMMARY` + catalog `page-registry-v4`；
  - Admin Web `/technician-portal/*` shell；
  - PostgreSQL IT、MVC Security、ArchitectureTest、Admin E2E spec。
- 明确不做：
  - `GET /mobile-work-packages/{id}/status` 与离线工作包 runtime；
  - Network Portal 写命令；
  - 完整 Technician App（GPS、相机、大上传）；
  - Consumer Identity、评分、BUSINESS SLA。

## 事实源

- `api/06-application-query-preference-http-api.md` §0 / §11（M195 Accepted）
- ADR-033
- M188 Portal 上下文；M185 师傅目录；M194 Network Portal 模式

## 设计要点

- `X-Technician-Context` = `TECHNICIAN|NETWORK|{uuid}` 或经 ACTIVE NetworkTechnicianMembership
  校验的 UUID；
- 禁止 query-param networkId；伪造/非师傅 → `PORTAL_CONTEXT_INVALID`；
- 能力：`task.readAssigned`（NETWORK scope），不新增 capability 种子；
- Feed：ACTIVE TECHNICIAN ServiceAssignment（assignee=principalId 或 profileId）+ 同网点
  TaskAssignment；tombstone 仅 taskId + invalidationReason；
- schedule / sync-summary 为轻量 fan-in，无离线命令 runtime。

## 已实现

- [x] ADR-033
- [x] OpenAPI Core `0.87.0`
- [x] `TechnicianPortalQueryService` / Controller
- [x] dispatch `TechnicianActiveAssignmentQuery`
- [x] task `TechnicianTaskAssignmentFeedQuery`
- [x] appointment `TechnicianScheduleAppointmentQuery`
- [x] Admin Web Technician Portal shell + Feed/日程/摘要页
- [x] PostgresIT / Security MVC / ArchitectureTest / E2E spec

## 明确未实现

- 离线工作包 / mobile sync commands；
- Network 写命令与完整师傅 App；
- Admin work-queues §4。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.87.0
- IT：`TechnicianPortalFeedPostgresIT`
- MVC：`TechnicianPortalControllerSecurityTest`
- E2E：`technician-portal-feed.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,TechnicianPortalFeedPostgresIT,TechnicianPortalControllerSecurityTest
./mvnw -pl serviceos-contracts -am test
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
