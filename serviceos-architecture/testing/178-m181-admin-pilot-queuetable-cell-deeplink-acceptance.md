---
title: M181 Admin QueueTable 行内单元格深链验收
status: Implemented
milestone: M181
lastUpdated: 2026-07-17
---

# M181 Admin QueueTable 行内单元格深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M181-01 | 审核队列表格 projectId 单元格 → 项目详情 | GET Project 200 | `admin-pilot-smoke.spec.ts` |
| M181-02 | 未配置 linkColumns 的表格保持明文 | 无 RouterLink | 页面代码审查 |
| M181-03 | 试点验收登记 | `ADMIN-PILOT-08QL` | `verify-admin-smoke.sh` |

## 明确不做

- 删除关联资源条、SavedView、FieldOperation、多态 sourceId。
