---
title: M354 整改深链验收矩阵
status: Implemented
milestone: M354
lastUpdated: 2026-07-19
---

# M354 整改深链验收矩阵

| ID | 级别 | 场景 | 证据 |
|---|---|---|---|
| M354-01 | P0 | REJECTED 自动创建 CorrectionCase/Task | `CorrectionCasePostgresIT` |
| M354-02 | P0 | Correction 详情深链回源 ReviewCase | `CorrectionCaseDetailPage.vue` |
| M354-03 | P0 | 补传产生新 Revision，原版保留 | Evidence/Correction IT |
| M354-04 | P1 | 前端不调用创建 Correction API | 终审提交仅 `:decide` |
