---
title: M242 Network Portal 整改详情残余 Accepted 字段展示
status: Implemented
milestone: M242
lastUpdated: 2026-07-17
relatedMilestones: [M202, M209, M241]
---

# M242 Network Portal 整改详情残余 Accepted 字段展示

## 目标

补齐整改详情上已 Accepted 但未展示的 `closed*`/`waived*` 操作者与补传 `submittedBy`。

## 范围与非目标

- 范围：ADR-080；`NetworkPortalCorrectionDetailPage` enrichment；OpenAPI 仍 1.0.16；
  catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、摘要扩 waiveNote/actor、Portal close/waive 写控件、PII、notifications。

## 事实源

- `decisions/ADR-080-network-portal-correction-detail-residual-fields.md`
- ADR-047 / M209；Core OpenAPI `CorrectionCase` / `CorrectionResubmission`

## 设计要点

- UI-only：GET 详情已返回完整 DTO；仅补展示与 testid。
- `waiveNote` 仅详情展示，不回流工作区/目录摘要。

## 已实现

- [x] ADR-080
- [x] closedBy / waivedBy / waiveApprovalRef / waiveNote
- [x] resubmissions.submittedBy
- [x] E2E `network-portal-correction-detail-residual-fields.spec.ts`

## 明确未实现

Portal close/waive 写控件、摘要扩 waiveNote、notifications、PII。

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
