---
title: M207 Network Portal 工作台能力门控计数增强
status: Implemented
milestone: M207
lastUpdated: 2026-07-17
relatedMilestones: [M194, M202, M203, M205]
---

# M207 Network Portal 工作台能力门控计数增强

## 目标

在既有 workbench 上附加能力门控发现计数，并渲染 capacity 与深链。

## 范围与非目标

- 范围：
  - ADR-045：扩展 `GET /network-portal/workbench`；
  - 可选计数 + 缺能力省略；
  - Core OpenAPI `0.99.0`；Flyway 仍 100/102；
  - Page Registry `page-registry-v14`；
  - Admin Web 工作台 UI + E2E；
  - PostgreSQL IT、MVC Security、ArchitectureTest。
- 明确不做：SLA 计数、产能申请、Portal ACK、新 capability。

## 事实源

- ADR-045；product/03 §4；M194 workbench；M202/M203/M205 队列

## 设计要点

- 基座门禁不变：`networkTask.read`；
- enrichment：`authorize` 后计数；缺能力省略属性；
- `unassignedTechnicianTaskCount` 始终随基座返回。

## 已实现

- [x] ADR-045
- [x] OpenAPI Core `0.99.0`
- [x] NetworkPortalWorkbenchView 可选计数
- [x] workbench fan-in + JsonInclude NON_NULL
- [x] Page Registry v14
- [x] Admin Web + E2E
- [x] PostgresIT / Security MVC / ArchitectureTest

## 明确未实现

- SLA 风险计数；产能申请；即将到期窗。

## 工程证据

- OpenAPI：`serviceos-core-v1.yaml` 0.99.0
- IT：`NetworkPortalWorkbenchEnrichmentPostgresIT`
- MVC：`NetworkPortalControllerSecurityTest`
- E2E：`network-portal-workbench-enrichment.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test \
  -Dtest=ArchitectureTest,NetworkPortalWorkbenchEnrichmentPostgresIT,NetworkPortalControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
