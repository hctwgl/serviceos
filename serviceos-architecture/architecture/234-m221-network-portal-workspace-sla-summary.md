---
title: M221 Network Portal 工作区薄 SLA 摘要
status: Implemented
milestone: M221
lastUpdated: 2026-07-17
relatedMilestones: [M213, M207, M65]
---

# M221 Network Portal 工作区薄 SLA 摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `slaSummary.openCount/breachedCount`。

## 范围与非目标

- 范围：ADR-059；OpenAPI 1.0.1；NETWORK `sla.read` soft-gate；按 ACTIVE taskIds 计数；
  Admin Web 展示；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：Visit/表单、工作台 SLA 风险计数、Admin workspace 复用、PII、SLA 详情。

## 已实现

- [x] ADR-059
- [x] OpenAPI 1.0.1 + DTO/编排
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
