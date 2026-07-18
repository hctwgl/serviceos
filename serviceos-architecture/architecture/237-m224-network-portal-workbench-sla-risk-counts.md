---
title: M224 Network Portal 工作台薄 SLA 风险计数
status: Implemented
milestone: M224
lastUpdated: 2026-07-17
relatedMilestones: [M207, M221]
---

# M224 Network Portal 工作台薄 SLA 风险计数

## 目标

在 Network Portal 工作台上接受并交付非 PII 的 `slaSummary` 风险计数 enrichment。

## 范围与非目标

- 范围：ADR-062；OpenAPI 1.0.4；NETWORK `sla.read` soft-gate；
  字段复用 M221 `openCount`/`breachedCount`；按本网点全部 ACTIVE taskIds 聚合；
  Admin Web 展示；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：即将超时时间窗、SLA 详情/deeplink、notifications、Portal ACK、
  Admin SLA 队列复用、BUSINESS 时钟、新 pageId。

## 已实现

- [x] ADR-062
- [x] OpenAPI 1.0.4 + DTO/编排
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
