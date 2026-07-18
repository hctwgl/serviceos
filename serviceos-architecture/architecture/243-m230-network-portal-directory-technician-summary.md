---
title: M230 Network Portal 目录页师傅服务端摘要
status: Implemented
milestone: M230
lastUpdated: 2026-07-17
relatedMilestones: [M194, M217, M228, M229]
---

# M230 Network Portal 目录页师傅服务端摘要

## 目标

在工单/任务目录列表页包装上接受并交付非 PII 的 `technicians[]` 摘要 enrichment，
替换 M217 客户端 fan-in。

## 范围与非目标

- 范围：ADR-068；OpenAPI 1.0.10；NETWORK `technician.readOwnNetwork` soft-gate；
  `$ref` `NetworkPortalTechnicianItem`；按本页 `items[].technicianId` 命中过滤；
  catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：PII、写控件字段发明、Admin workspace 复用、notifications、新 pageId、
  列表预约 N+1、Portal ACK、产能申请。

## 事实源

- `decisions/ADR-068-network-portal-directory-technician-summary.md`
- `decisions/ADR-066-network-portal-workspace-technician-summary.md`
- `api/06-application-query-preference-http-api.md` §10
- `architecture/230-m217-network-portal-directory-technician-fanin.md`

## 设计要点

- 扩展 `NetworkPortalPage` 可选 `technicians`（JSON `NON_NULL`）；仅 work-orders/tasks
  列表填充；其它列表保持省略。
- soft-gate 与 M228 相同；缺能力省略；有能力无命中返回 `[]`。
- Admin Web：优先消费页级 `technicians`；缺字段时回退 M217 client fan-in（兼容旧桩）。
- 任务页指派下拉仍调用 `GET /technicians` 获取 ACTIVE 候选全集。

## 已实现

- [x] ADR-068
- [x] OpenAPI 1.0.10 + DTO/编排
- [x] PostgresIT + Security
- [x] Admin Web + E2E

## 明确未实现

- notifications / `NETWORK.NOTIFICATION`
- Portal ACK/decide
- 列表预约服务端摘要
- 产能申请

## 工程证据

- OpenAPI 1.0.10；Flyway 100/102；catalog `page-registry-v16`
- `NetworkPortalReadPostgresIT` / `NetworkPortalControllerSecurityTest`
- Admin Web E2E `network-portal-directory-technician-summary.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
