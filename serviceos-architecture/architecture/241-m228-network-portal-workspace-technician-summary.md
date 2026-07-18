---
title: M228 Network Portal 工作区当前师傅服务端摘要
status: Implemented
milestone: M228
lastUpdated: 2026-07-17
relatedMilestones: [M213, M216, M194, M227]
---

# M228 Network Portal 工作区当前师傅服务端摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `technicians[]` 摘要 enrichment，替换 M216
客户端 fan-in。

## 范围与非目标

- 范围：ADR-066；OpenAPI 1.0.8；NETWORK `technician.readOwnNetwork` soft-gate；
  `$ref` `NetworkPortalTechnicianItem`；按工作区 technicianId 命中过滤；catalog 仍 v16；
  Flyway 仍 100/102；IT/E2E。
- 明确不做：PII、写控件、Admin workspace 复用、notifications、新 pageId。

## 已实现

- [x] ADR-066
- [x] OpenAPI 1.0.8 + DTO/编排
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
