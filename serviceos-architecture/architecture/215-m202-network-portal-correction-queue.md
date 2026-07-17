---
title: M202 Network Portal 整改队列只读
status: Implemented
milestone: M202
lastUpdated: 2026-07-17
relatedMilestones: [M98, M194, M201]
---

# M202 Network Portal 整改队列只读

## 目标

在 M201 资料代补之上，交付 Network Portal 本网点整改案例只读发现面，使网点可定位
未关闭整改并深链到代补动作。

## 范围与非目标

- 范围：
  - ADR-040：list/get network-portal correction-cases；
  - 复用 ACTIVE NETWORK 任务集合 + `evidence.read` NETWORK；
  - Core OpenAPI `0.94.0`；Flyway 仍 099/101；
  - Page Registry `NETWORK.CORRECTION.QUEUE` + catalog `page-registry-v9`；
  - Admin Web `/network-portal/corrections` + E2E；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：
  - Admin 项目范围 cursor 队列原样移植；
  - 资质/产能/异常 Network 写；
  - 离线工作包、完整 product/03 UI。

## 事实源

- ADR-040；ADR-032；API-06 §6（Admin 队列字段语义参考）/ §10 窄扩展
- M201 代补；M98 CorrectionCaseQueueItem；M194 Network Portal fan-in

## 设计要点

- 门禁：`X-Network-Context` → ACTIVE membership → `evidence.read` NETWORK；
- list：ACTIVE NETWORK tasks → `CorrectionCaseService.listForTask` 聚合过滤 status；
- get：CorrectionCaseService.get + ACTIVE NETWORK 责任校验；
- 响应：`NetworkPortalPage` + 队列安全摘要字段。

## 已实现

- [x] ADR-040
- [x] OpenAPI Core `0.94.0`
- [x] NetworkPortalQueryService list/get corrections
- [x] Page Registry v9
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- Admin cursor 语义；资质/产能申请；异常队列。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.94.0
- IT：`NetworkPortalCorrectionQueuePostgresIT`
- MVC：扩展 `NetworkPortalControllerSecurityTest`
- E2E：`network-portal-correction-queue.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalCorrectionQueuePostgresIT,NetworkPortalControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
