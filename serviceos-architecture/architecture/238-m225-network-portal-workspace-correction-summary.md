---
title: M225 Network Portal 工作区整改摘要
status: Implemented
milestone: M225
lastUpdated: 2026-07-17
relatedMilestones: [M213, M214, M202, M223, M90]
---

# M225 Network Portal 工作区整改摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `corrections[]` 摘要 enrichment。

## 范围与非目标

- 范围：ADR-063；OpenAPI 1.0.5；NETWORK `evidence.read` soft-gate；
  字段复用 Admin `WorkOrderWorkspaceCorrectionCaseSummary`；按 ACTIVE taskIds fan-in；
  替换 M214 客户端 OPEN-only fan-in；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：reviews[]、Portal ACK、Admin workspace 复用、notifications、新 pageId。

## 已实现

- [x] ADR-063
- [x] OpenAPI 1.0.5 + DTO/编排
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
