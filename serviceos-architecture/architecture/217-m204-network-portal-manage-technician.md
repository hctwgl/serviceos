---
title: M204 Network Portal 师傅关系与资质提交
status: Implemented
milestone: M204
lastUpdated: 2026-07-17
relatedMilestones: [M185, M194, M196]
---

# M204 Network Portal 师傅关系与资质提交

## 目标

在 M185 目录命令与 M194 师傅只读之上，交付 Network Portal 本网点师傅关系绑定/终止
与资质 PENDING 提交写切片。

## 范围与非目标

- 范围：
  - ADR-042：membership create/terminate + qualification submit；
  - `networkPortal.manageTechnician` + NETWORK 收窄 `network.manageTechnician`；
  - Core OpenAPI `0.96.0`；Flyway V100 / 102；
  - Page Registry `NETWORK.QUALIFICATION` + catalog `page-registry-v11`；
  - Admin Web 控件 + E2E；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：
  - Portal decide、FileObject、createTechnicianProfile、产能申请。

## 事实源

- ADR-042；ADR-024/034；product/03 §10；M185 NetworkCommandService

## 设计要点

- Portal 门禁：`networkPortal.manageTechnician` NETWORK；
- ThreadLocal 网络范围使 `begin()` 走 `network.manageTechnician` NETWORK；
- submit 要求 ACTIVE NetworkTechnicianMembership。

## 已实现

- [x] ADR-042
- [x] OpenAPI Core `0.96.0`
- [x] NetworkAuthorizationPort NETWORK + scoped begin
- [x] NetworkPortalManageTechnicianService / Controller
- [x] Flyway V100
- [x] Page Registry v11
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- Portal decide / FileObject；产能申请。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.96.0
- IT：`NetworkPortalManageTechnicianPostgresIT`
- MVC：`NetworkPortalManageTechnicianControllerSecurityTest`
- E2E：`network-portal-manage-technician.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalManageTechnicianPostgresIT,NetworkPortalManageTechnicianControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
