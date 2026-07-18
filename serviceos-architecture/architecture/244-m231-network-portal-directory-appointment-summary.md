---
title: M231 Network Portal 目录页预约服务端摘要
status: Implemented
milestone: M231
lastUpdated: 2026-07-17
relatedMilestones: [M194, M215, M227, M230]
---

# M231 Network Portal 目录页预约服务端摘要

## 目标

在工单/任务目录列表页包装上接受并交付非 PII 的 `appointments[]` 摘要 enrichment，
闭合 product/03 §5「预约窗口」目录列，避免客户端 N+1。

## 范围与非目标

- 范围：ADR-069；OpenAPI 1.0.11；NETWORK `networkPortal.manageAppointment` soft-gate；
  `$ref` Admin/NP 预约摘要；按本页 taskIds 命中 + networkId 过滤；catalog 仍 v16；
  Flyway 仍 100/102；IT/E2E。
- 明确不做：完整 Appointment DTO、写控件、PII、目录 contactAttempts、notifications、
  Portal ACK、新 pageId。

## 事实源

- `decisions/ADR-069-network-portal-directory-appointment-summary.md`
- `decisions/ADR-065-network-portal-workspace-appointment-contact-summary.md`
- `api/06-application-query-preference-http-api.md` §10
- `architecture/243-m230-network-portal-directory-technician-summary.md`

## 设计要点

- 扩展 `NetworkPortalPage` 可选 `appointments`（JSON `NON_NULL`）；仅 work-orders/tasks
  列表填充。
- soft-gate 与 M227 相同；复用 `loadAppointmentSummaries`。
- Admin Web：目录「预约窗口」列由页级 `appointments` 解析；缺字段时省略窗口（不 N+1）。

## 已实现

- [x] ADR-069
- [x] OpenAPI 1.0.11 + DTO/编排
- [x] PostgresIT + Security
- [x] Admin Web + E2E

## 明确未实现

- 目录 contactAttempts 旁载
- notifications / `NETWORK.NOTIFICATION`
- Portal ACK/decide

## 工程证据

- OpenAPI 1.0.11；Flyway 100/102；catalog `page-registry-v16`
- `NetworkPortalReadPostgresIT`
- Admin Web E2E `network-portal-directory-appointment-summary.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
