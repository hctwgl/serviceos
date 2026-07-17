---
title: M179 Admin 剩余详情页 projectId 深链验收
status: Implemented
milestone: M179
lastUpdated: 2026-07-17
---

# M179 Admin 剩余详情页 projectId 深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M179-01 | 整改详情 → 项目详情 | GET Project 200 | `admin-pilot-smoke.spec.ts` |
| M179-02 | 表单/资料/预约/上门/SLA/任务/入站/回执展示 projectId 深链 | RouterLink 指向 `ADMIN.PROJECT.DETAIL` | 页面代码审查 |
| M179-03 | 试点验收登记 | `ADMIN-PILOT-08PP` | `verify-admin-smoke.sh` |

## 明确不做

- QueueTable 行内单元格链接、FieldOperation、ReviewRoute、SavedView、ServiceNetwork。
