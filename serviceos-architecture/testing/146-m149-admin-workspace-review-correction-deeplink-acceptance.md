---
title: M149 Admin 工作区审核/整改详情深链验收
status: Implemented
lastUpdated: 2026-07-17
---

# M149 Admin 工作区审核/整改详情深链验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M149-01 | REVIEWS_CORRECTIONS 渲染审核链接 | `reviews[]` → 「打开审核案例」 | PASS |
| M149-02 | 审核深链打开详情 | GET `/review-cases/{id}` 200；标题「审核案例」 | PASS |
| M149-03 | REVIEWS_CORRECTIONS 渲染整改链接 | `corrections[]` → 「打开整改案例」 | PASS |
| M149-04 | 整改深链打开详情 | GET `/correction-cases/{id}` 200；标题「整改案例」 | PASS |
| M149-05 | 试点验收登记 | `ADMIN-PILOT-08RD` | PASS |

不证明入站队列列表、SavedView、异常队列筛选或真实 sandbox。
