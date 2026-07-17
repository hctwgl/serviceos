---
title: M220 Network Portal 队列/列表 Accepted 字段展示
status: Implemented
milestone: M220
lastUpdated: 2026-07-17
relatedMilestones: [M194, M202, M203, M205, M206, M217, M218]
---

# M220 Network Portal 队列/列表 Accepted 字段展示

## 目标

补齐整改/异常/资质/师傅列表与任务目录上已 Accepted 但未展示的非 PII 字段，并闭合异常
`handlingTaskId` 深链缺口。

## 范围与非目标

- 范围：ADR-058；四列表 enrichment；异常详情 handlingTaskId 深链；任务目录
  businessType/effectiveFrom；OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、ACK/decide、Admin Review 深链、SLA/Visit/表单、notifications、PII。

## 已实现

- [x] ADR-058
- [x] Corrections/Exceptions/Qualifications/Technicians list enrichment
- [x] Exception detail handlingTaskId deeplink
- [x] Tasks list businessType/effectiveFrom
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
