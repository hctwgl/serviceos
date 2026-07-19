---
title: M354 终审驳回整改深链与版本链
status: Implemented
milestone: M354
lastUpdated: 2026-07-19
relatedMilestones: [M353, M45, M47, M149, M164]
openapiVersion: "1.0.49"
flywayVersion: "130"
---

# M354 终审驳回整改深链与版本链

## 目标

验证并接通 REJECTED → CorrectionCase/整改 Task → Admin 深链 → 补传新 Revision → 原 Revision 保留 → resubmit/后继 ReviewCase 的闭环，不由前端创建 Correction。

## 已实现 / 证据

- 驳回同事务创建 Correction：`DefaultReviewCaseService.decide` + `CorrectionCasePostgresIT`
- Correction 详情含 `sourceReviewCaseId` 深链：`CorrectionCaseDetailPage.vue`
- 工单工作区 REVIEWS_CORRECTIONS / FINAL_REVIEW 可导航至审核与整改
- 补传不覆盖原 Revision：既有 Evidence Revision 只追加语义 + Correction IT

## 明确未实现

终审后更正批次；独立审核 Task 与提交 Task 分离的模板化编排。

## 验证

```bash
bash scripts/agent-verify.sh it CorrectionCasePostgresIT,ReviewCasePostgresIT
```
