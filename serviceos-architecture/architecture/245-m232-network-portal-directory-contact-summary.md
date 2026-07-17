---
title: M232 Network Portal 目录页联系尝试服务端摘要
status: Implemented
milestone: M232
lastUpdated: 2026-07-17
relatedMilestones: [M199, M227, M231]
---

# M232 Network Portal 目录页联系尝试服务端摘要

## 目标

在工单/任务目录列表页包装上接受并交付非 PII 的 `contactAttempts[]` 摘要 enrichment，
闭合 ADR-069/M231 明确未实现的目录联系旁载，避免客户端 N+1。

## 范围与非目标

- 范围：ADR-070；OpenAPI 1.0.12；NETWORK `networkPortal.manageAppointment` soft-gate；
  `$ref` Admin/NP 联系摘要；按本页 taskIds 命中；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：PII/party/note/recording/actor、写控件、notifications、Portal ACK、新 pageId。

## 事实源

- `decisions/ADR-070-network-portal-directory-contact-summary.md`
- `decisions/ADR-069-network-portal-directory-appointment-summary.md`
- `api/06-application-query-preference-http-api.md` §10

## 设计要点

- 扩展 `NetworkPortalPage` 可选 `contactAttempts`（JSON `NON_NULL`）。
- soft-gate 与 M231 预约旁载相同；复用 `loadContactAttemptSummaries`。
- Admin Web：「最近联系」列由页级 `contactAttempts` 解析；缺字段时省略列。

## 已实现

- [x] ADR-070
- [x] OpenAPI 1.0.12 + DTO/编排
- [x] PostgresIT
- [x] Admin Web + E2E

## 明确未实现

- notifications / `NETWORK.NOTIFICATION`
- Portal ACK/decide

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
