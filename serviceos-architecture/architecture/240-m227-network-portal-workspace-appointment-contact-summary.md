---
title: M227 Network Portal 工作区预约/联系服务端摘要
status: Implemented
milestone: M227
lastUpdated: 2026-07-17
relatedMilestones: [M213, M215, M197, M199, M222, M226]
---

# M227 Network Portal 工作区预约/联系服务端摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `appointments[]` / `contactAttempts[]`
摘要 enrichment，替换 M215 客户端 fan-in。

## 范围与非目标

- 范围：ADR-065；OpenAPI 1.0.7；NETWORK `networkPortal.manageAppointment` soft-gate；
  `$ref` Admin `WorkOrderWorkspaceAppointmentSummary` /
  `WorkOrderWorkspaceContactAttemptSummary`；ACTIVE taskIds；预约按 networkId 过滤；
  catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：完整 Appointment DTO、写控件、PII、Admin workspace 复用、notifications、
  师傅服务端摘要、新 pageId。

## 已实现

- [x] ADR-065
- [x] OpenAPI 1.0.7 + DTO/编排
- [x] PostgresIT + Security
- [x] Admin Web + E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
