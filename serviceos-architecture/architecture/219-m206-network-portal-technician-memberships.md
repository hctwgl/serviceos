---
title: M206 Network Portal 师傅关系只读列表
status: Implemented
milestone: M206
lastUpdated: 2026-07-17
relatedMilestones: [M185, M194, M204, M205]
---

# M206 Network Portal 师傅关系只读列表

## 目标

在 M204 师傅关系写命令之上，交付本网点师傅关系只读列表/详情，暴露真实 `version`
供 terminate If-Match。

## 范围与非目标

- 范围：
  - ADR-044：list/get network-portal technician-memberships；
  - 复用 `technician.readOwnNetwork` NETWORK；
  - Core OpenAPI `0.98.0`；Flyway 仍 100/102；
  - Page Registry catalog `page-registry-v13`；
  - Admin Web 使用列表 version 填充终止表单 + E2E；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：操作员成员邀请、Portal decide、产能申请。

## 事实源

- ADR-044；ADR-042；M185 NetworkTechnicianMembership；M194 technicians

## 设计要点

- 门禁：membership + `technician.readOwnNetwork`；
- 默认 status=ACTIVE；可查 TERMINATED（仍限本网点）；
- get：serviceNetworkId 必须等于上下文。

## 已实现

- [x] ADR-044
- [x] OpenAPI Core `0.98.0`
- [x] NetworkPortalMembershipQuery + readmodel list/get
- [x] Page Registry v13
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- 操作员 NetworkMembership；产能申请；Portal decide。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.98.0
- IT：`NetworkPortalTechnicianMembershipPostgresIT`
- MVC：`NetworkPortalControllerSecurityTest`
- E2E：`network-portal-technician-memberships.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalTechnicianMembershipPostgresIT,NetworkPortalControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
