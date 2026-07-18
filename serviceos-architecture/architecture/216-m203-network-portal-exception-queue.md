---
title: M203 Network Portal 运营异常队列只读
status: Implemented
milestone: M203
lastUpdated: 2026-07-17
relatedMilestones: [M29, M100, M194, M202]
---

# M203 Network Portal 运营异常队列只读

## 目标

在 M202 整改队列同构模式下，交付 Network Portal 本网点运营异常只读发现面。

## 范围与非目标

- 范围：
  - ADR-041：list/get network-portal operational-exceptions；
  - `operations.listForTask` + NETWORK 鉴权携带；
  - Core OpenAPI `0.95.0`；Flyway 仍 099/101；
  - Page Registry `NETWORK.EXCEPTION.QUEUE` + `page-registry-v10`；
  - Admin Web `/network-portal/exceptions` + E2E；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：
  - Portal acknowledge/resolve；
  - Admin cursor 队列移植；
  - 资质/产能写、完整 product/03 UI。

## 事实源

- ADR-041；ADR-032/040；product/03 §12；API-06 §10 窄扩展

## 设计要点

- 门禁：`X-Network-Context` → membership → `operations.exception.read` NETWORK；
- list：ACTIVE NETWORK tasks → `listForTask` 聚合过滤 status/severity；
- get：Workbench get（NETWORK 可匹配）+ ACTIVE NETWORK 责任校验；
- Portal `allowedActions` 恒为空。

## 已实现

- [x] ADR-041
- [x] OpenAPI Core `0.95.0`
- [x] OperationalExceptionWorkbenchService.listForTask + NETWORK get auth
- [x] NetworkPortalQueryService list/get exceptions
- [x] Page Registry v10
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- Portal ACK；资质/产能申请；异常策略过滤扩展。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.95.0
- IT：`NetworkPortalExceptionQueuePostgresIT`
- MVC：`NetworkPortalControllerSecurityTest`
- E2E：`network-portal-exception-queue.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalExceptionQueuePostgresIT,NetworkPortalControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
