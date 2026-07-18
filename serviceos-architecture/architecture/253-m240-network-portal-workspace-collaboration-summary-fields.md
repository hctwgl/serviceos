---
title: M240 Network Portal 工作区协作摘要 Accepted 字段展示
status: Implemented
milestone: M240
lastUpdated: 2026-07-17
relatedMilestones: [M225, M226, M227, M228, M229, M239]
---

# M240 Network Portal 工作区协作摘要 Accepted 字段展示

## 目标

补齐工作区预约 / 联系 / 整改 / 审核 / 异常 / 师傅摘要上已 Accepted 但未展示的非 PII 字段，
闭合 product/03 §6.1 协作显示面。

## 范围与非目标

- 范围：ADR-078；`NetworkPortalWorkOrderWorkspacePage` enrichment；任务页联系历史时间字段
  TS+展示；OpenAPI 仍 1.0.16；catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、摘要扩 actor、PII、Portal ACK、notifications、Admin workspace 复用。

## 事实源

- `product/03-network-portal-spec.md` §6.1
- `decisions/ADR-078-network-portal-workspace-collaboration-summary-fields.md`
- Core OpenAPI workspace collaboration summaries / `NetworkPortalExceptionItem` /
  `NetworkPortalTechnicianItem` / `ContactAttempt`

## 设计要点

- UI-only：既有 soft-gate 摘要字段已齐；仅补展示、深链与 testid。
- correctionTaskId / handlingTaskId → `/network-portal/tasks?taskId=`。
- 最新 resubmission / decision 以单行摘要展示，不展开完整数组 UI。

## 已实现

- [x] ADR-078
- [x] 工作区协作摘要 Accepted 字段展示
- [x] 任务页联系历史时间字段（可选附带）
- [x] E2E `network-portal-workspace-collaboration-summary-fields.spec.ts`

## 明确未实现

摘要扩 actor/createdBy、客户 PII、Portal ACK、notifications、Admin workspace 复用。

## 工程证据

- OpenAPI 仍 1.0.16；Flyway 仍 100/102；catalog 仍 `page-registry-v16`
- Admin Web E2E + `bash scripts/verify-local.sh`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
