---
title: M177 Admin 外发/异常/入站队列源资源深链验收
status: Implemented
milestone: M177
lastUpdated: 2026-07-17
---

# M177 Admin 外发/异常/入站队列源资源深链验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M177-01 | 外发队列 → 源任务 | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M177-02 | 异常队列 → 人工接管任务 | GET Task 200 | `admin-pilot-smoke.spec.ts` |
| M177-03 | 入站队列 → 项目详情 | GET Project 200 | `admin-pilot-smoke.spec.ts` |
| M177-04 | 试点验收登记 | `ADMIN-PILOT-08QO` | `verify-admin-smoke.sh` |

## 明确不做

- QueueTable 行内单元格链接、多态 sourceId、SavedView、FieldOperation。
