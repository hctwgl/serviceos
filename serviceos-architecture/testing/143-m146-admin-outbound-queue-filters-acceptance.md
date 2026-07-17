---
title: M146 Admin 外发交付队列筛选验收
status: Implemented
lastUpdated: 2026-07-17
---

# M146 Admin 外发交付队列筛选验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M146-01 | 默认 UNKNOWN 切片 | 页面默认 `status=UNKNOWN`；与 OpenAPI 省略默认一致 | PASS |
| M146-02 | ACKNOWLEDGED + sourceWorkOrderId 筛选 | GET 两参数后可见目标 `externalOrderCode`（避免历史 ACK 分页遮挡） | PASS |
| M146-03 | 深链仍可用 | 筛选结果链接打开交付详情 | PASS |
| M146-04 | 试点验收登记 | `ADMIN-PILOT-08OQ` | PASS |

不证明多状态 OR、SavedView、入站队列列表或真实 sandbox。
