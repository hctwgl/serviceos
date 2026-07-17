---
title: M176 Admin 专项队列关联资源深链验收
status: Implemented
milestone: M176
lastUpdated: 2026-07-17
---

# M176 Admin 专项队列关联资源深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M176-01 | 整改队列 → 源审核 | GET ReviewCase 200 | `admin-pilot-smoke.spec.ts` |
| M176-02 | 整改队列 → 整改 Task | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M176-03 | 审核队列展示 project/task 深链 | RouterLink 指向已有详情路由 | 页面代码审查 |
| M176-04 | 试点验收登记 | `ADMIN-PILOT-08QC` | `verify-admin-smoke.sh` |

## 明确不做

- QueueTable 行内单元格链接、SavedView、FieldOperation、ServiceNetwork。
