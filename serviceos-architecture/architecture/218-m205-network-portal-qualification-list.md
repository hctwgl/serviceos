---
title: M205 Network Portal 本网点资质只读列表
status: Implemented
milestone: M205
lastUpdated: 2026-07-17
relatedMilestones: [M185, M194, M204]
---

# M205 Network Portal 本网点资质只读列表

## 目标

在 M204 资质提交之上，交付 Network Portal 本网点师傅资质只读发现面。

## 范围与非目标

- 范围：
  - ADR-043：list/get network-portal technician-qualifications；
  - 复用 ACTIVE NetworkTechnicianMembership + `technician.readOwnNetwork`；
  - Core OpenAPI `0.97.0`；Flyway 仍 100/102；
  - Page Registry `page-registry-v12`；
  - Admin Web `/network-portal/qualifications` + E2E；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：Portal decide、FileObject、产能申请。

## 事实源

- ADR-043；ADR-042；product/03 §10；M185 TechnicianQualification

## 设计要点

- 门禁：membership + `technician.readOwnNetwork` NETWORK；
- fan-in：ACTIVE 师傅集合上的 qualifications；
- get：资质所属师傅须对本网点 ACTIVE。

## 已实现

- [x] ADR-043
- [x] OpenAPI Core `0.97.0`
- [x] NetworkPortalQualificationQuery + readmodel list/get
- [x] Page Registry v12
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- Portal decide / FileObject；产能申请。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.97.0
- IT：`NetworkPortalQualificationListPostgresIT`
- MVC：`NetworkPortalControllerSecurityTest`
- E2E：`network-portal-qualification-list.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalQualificationListPostgresIT,NetworkPortalControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
