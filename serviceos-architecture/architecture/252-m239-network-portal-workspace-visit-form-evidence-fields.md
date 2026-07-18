---
title: M239 Network Portal 工作区 Visit/表单/Evidence Accepted 字段展示
status: Implemented
milestone: M239
lastUpdated: 2026-07-17
relatedMilestones: [M222, M223, M238]
---

# M239 Network Portal 工作区 Visit/表单/Evidence Accepted 字段展示

## 目标

补齐工作区 Visit / 表单提交 / Evidence 摘要上已 Accepted 但未展示的非 PII 字段，闭合
product/03 §6.1 显示面。

## 范围与非目标

- 范围：ADR-077；`NetworkPortalWorkOrderWorkspacePage` enrichment；OpenAPI 仍 1.0.16；
  catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、GPS/note/values/definition/file、Admin workspace 复用、PII、
  notifications、Portal ACK、摘要扩 actor。

## 事实源

- `product/03-network-portal-spec.md` §6.1
- `decisions/ADR-077-network-portal-workspace-visit-form-evidence-fields.md`
- Core OpenAPI `WorkOrderWorkspaceVisitSummary` / `FormSubmissionSummary` /
  `EvidenceSlotSummary` / `EvidenceItemSummary`

## 设计要点

- UI-only：M222/M223 契约与 TS 类型已齐；仅补展示与 testid。
- 继续尊重既有 soft-gate 与「缺能力省略属性」语义。

## 已实现

- [x] ADR-077
- [x] Visit / 表单 / Evidence 槽位与资料项 Accepted 字段展示
- [x] E2E `network-portal-workspace-visit-form-evidence-fields.spec.ts`

## 明确未实现

GPS/note/values/definition/缩略图、Admin workspace 复用、notifications、Portal ACK、PII。

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
