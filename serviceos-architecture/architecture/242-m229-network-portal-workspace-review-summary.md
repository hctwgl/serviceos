---
title: M229 Network Portal 工作区审核案例服务端摘要
status: Implemented
milestone: M229
lastUpdated: 2026-07-17
relatedMilestones: [M213, M225, M90, M96, M223]
---

# M229 Network Portal 工作区审核案例服务端摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `reviews[]` 摘要 enrichment，闭合 Admin
`REVIEWS_CORRECTIONS` 在 NP 侧的 reviews 半侧。

## 范围与非目标

- 范围：ADR-067；OpenAPI 1.0.9；NETWORK `evidence.read` soft-gate；
  `$ref` Admin Review 摘要；ACTIVE taskIds；`ReviewCaseService.listForTask` NETWORK
  scope 对齐；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：独立 NP Review API/pageId、Portal ACK、Admin Review 深链、
  note/approvalRef/decidedBy、notifications。

## 已实现

- [x] ADR-067
- [x] OpenAPI 1.0.9 + DTO/编排 + ReviewCase NETWORK read
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
