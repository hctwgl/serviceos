---
title: M214 Network Portal 工作区协作队列深链与 query 水合
status: Implemented
milestone: M214
lastUpdated: 2026-07-17
relatedMilestones: [M213, M202, M203, M197, M209, M210]
---

# M214 Network Portal 工作区协作队列深链与 query 水合

## 目标

在 M213 薄工作区上，打通到既有整改/异常/任务协作面的深链，并让目标页水合 `taskId` query。

## 范围与非目标

- 范围：ADR-052；工作区任务行深链；corrections/exceptions/tasks query 水合；
  工作区可选 fan-in OPEN 整改/异常摘要（缺能力省略）；OpenAPI 仍 1.0.0；
  catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP/字段、SLA/Visit/表单 DTO、PII、Portal ACK、notifications。

## 已实现

- [x] ADR-052
- [x] Workspace 深链 + related 摘要
- [x] Tasks/Corrections/Exceptions query 水合
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
