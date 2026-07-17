---
title: M180 Admin 专项队列剩余关联资源深链验收
status: Implemented
milestone: M180
lastUpdated: 2026-07-17
---

# M180 Admin 专项队列剩余关联资源深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M180-01 | 审核队列 → 资料快照 | GET Snapshot 200 | `admin-pilot-smoke.spec.ts` |
| M180-02 | 整改队列 → 项目 / 来源任务 | GET Project / Task 200 | `admin-pilot-smoke.spec.ts` |
| M180-03 | 可空关联字段条件渲染 | `latestResubmissionSnapshotId` / `sourceReviewCaseId` / `reopenedFromReviewCaseId` | 页面代码审查 |
| M180-04 | 试点验收登记 | `ADMIN-PILOT-08QR` | `verify-admin-smoke.sh` |

## 明确不做

- QueueTable 行内单元格链接、SavedView、FieldOperation、ServiceNetwork。
