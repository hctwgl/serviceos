---
title: M222 Network Portal 工作区 Visit/表单提交摘要
status: Implemented
milestone: M222
lastUpdated: 2026-07-17
relatedMilestones: [M213, M221, M88, M95]
---

# M222 Network Portal 工作区 Visit/表单提交摘要

## 目标

在限定工单工作区上接受并交付非 PII 的 `visits` / `formSubmissions` 摘要 enrichment。

## 范围与非目标

- 范围：ADR-060；OpenAPI 1.0.2；NETWORK `visit.read` / `form.read` soft-gate；
  字段复用 Admin `WorkOrderWorkspaceVisitSummary` /
  `WorkOrderWorkspaceFormSubmissionSummary`；按 ACTIVE taskIds + 本网点过滤；
  Admin Web 展示；catalog 仍 v16；Flyway 仍 100/102；IT/E2E。
- 明确不做：definition/values、Evidence 摘要、Admin workspace 复用、PII、独立 NP
  Visit/表单列表 API、工作台 SLA 风险、notifications。

## 已实现

- [x] ADR-060
- [x] OpenAPI 1.0.2 + DTO/编排 + NETWORK 作用域查询端口
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
