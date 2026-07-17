---
title: M147 Admin 工作区外发交付详情深链验收
status: Implemented
lastUpdated: 2026-07-17
---

# M147 Admin 工作区外发交付详情深链验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M147-01 | INTEGRATION 渲染外发链接 | `outboundDeliveries[]` → 「打开外发交付」 | PASS |
| M147-02 | 深链打开详情 | GET `/outbound-deliveries/{id}` 200；标题「外发交付」 | PASS |
| M147-03 | URL 与订单码 | `/integration/outbound/{deliveryId}`；可见 `externalOrderCode` | PASS |
| M147-04 | 试点验收登记 | `ADMIN-PILOT-08OD` | PASS |

不证明入站队列列表、SavedView、Review/Correction 队列筛选或真实 sandbox。
