---
title: M360 终审 8 态视觉基线与 admin-pilot 冒烟加固
status: Implemented
milestone: M360
lastUpdated: 2026-07-19
relatedMilestones: [M352, M353, M355]
openapiVersion: "1.0.53"
flywayVersion: "131"
---

# M360 终审 8 态视觉基线与 admin-pilot 冒烟加固

## 目标

冻结平台终审工作台固定视口（1440×1024）下的 8 态视觉截图金标，并纳入
admin-pilot Chrome 冒烟同门禁，关闭 M352/M355 遗留的「完整 8 态视觉基线」缺口。

## 范围与非目标

- 范围：
  - 文档化 8 态清单（loading / empty / error / pending / approved-ready /
    rejected-ready / readonly / conflict）；附加 stale-draft 警告截图；
  - Playwright Mock API 视觉套件 `final-review-visual.spec.ts` 实拍并提交金标 PNG；
  - 抽取 `final-review-fixtures.ts`；empty 态失败关闭修正（无数据 ≠ 加载失败）；
  - `AsyncContent` 稳定 `data-testid`；
  - `verify-admin-smoke.sh` 同步跑 visual 套件；
  - 无 OpenAPI / Flyway 变更（仍为 1.0.53 / 131）。
- 明确不做：
  - iOS 条件执行器 / 派单过滤；
  - 独立审核 `REVIEW_TASK` 工作流模板分离；
  - 吉利 / AMOUNT / BUSINESS SLA；
  - 终审真实 OIDC 深链写路径（仍由既有 admin-pilot-smoke 8 场景覆盖）。

## 8 态清单

| # | 状态 | 期望信号 | 金标 |
|---|---|---|---|
| 1 | loading | `async-content-loading` 骨架 | `final-review-loading.png` |
| 2 | empty | `async-content-empty`「暂无终审数据」 | `final-review-empty.png` |
| 3 | error | `async-content-error` 失败 detail | `final-review-error.png` |
| 4 | pending | 「提交终审」禁用 | `final-review-pending.png` |
| 5 | approved-ready | 「审核通过」可点 | `final-review-approved-ready.png` |
| 6 | rejected-ready | 「驳回整改」可点 | `final-review-rejected-ready.png` |
| 7 | readonly | 无 DECIDE；原因可见 | `final-review-readonly.png` |
| 8 | conflict | 「版本冲突」dialog | `final-review-conflict.png` |

附加：`final-review-stale-draft.png`（旧草稿警告条）。

## 已实现

- `tests/e2e/final-review-visual.spec.ts`（9 tests：8 态 + stale-draft）
- `tests/e2e/final-review-fixtures.ts` + workspace spec 复用
- `FinalReviewWorkspace` null data → empty
- `verify-admin-smoke.sh` 纳入 visual
- 金标 PNG 已提交

## 明确未实现

- 独立审核 HUMAN Task 模板分离；
- iOS 条件执行器与全量硬阻断；
- 派单级 `supportedClientKinds` 过滤。

## 验证命令

```bash
cd serviceos-admin-web && npm run test:unit && npm run build
npm run test:e2e -- tests/e2e/final-review-visual.spec.ts tests/e2e/final-review-workspace.spec.ts
# 完整真实 OIDC + Mock 终审 + 视觉（需 compose）：
bash serviceos-deploy/admin-pilot/verify-admin-smoke.sh
```
