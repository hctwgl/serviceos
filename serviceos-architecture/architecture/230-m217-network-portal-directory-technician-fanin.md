---
title: M217 Network Portal 目录页师傅 fan-in 与工作台基数深链
status: Implemented
milestone: M217
lastUpdated: 2026-07-17
relatedMilestones: [M194, M207, M213, M216]
---

# M217 Network Portal 目录页师傅 fan-in 与工作台基数深链

## 目标

把 M216 师傅 displayName fan-in 与深链模式扩展到工单/任务目录与工作台基数导航。

## 范围与非目标

- 范围：ADR-055；工单/任务目录师傅名 + 既有非 PII 列；工作台 ACTIVE 工单/任务/师傅深链；
  整改 correctionTaskId / 异常 workOrderId 深链；OpenAPI 仍 1.0.0；catalog 仍 v16；
  Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、SLA/Visit/表单、列表预约 N+1 fan-in、PII、notifications。

## 已实现

- [x] ADR-055
- [x] Work-orders / tasks directory enrichment
- [x] Workbench base-count deeplinks
- [x] Detail residual deeplinks
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
