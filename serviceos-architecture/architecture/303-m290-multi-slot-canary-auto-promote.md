---
title: M290 多槽位 CANARY 与满量自动晋级
status: Implemented
milestone: M290
lastUpdated: 2026-07-18
relatedMilestones: [M286, M288]
---

# M290 多槽位 CANARY 与满量自动晋级

## 已实现

1. `slot_code`（Flyway V117）；ACTIVE 唯一 `(tenant, project, channel, slot_code)`；
2. Resolve 按槽位累计流量哈希分流；合计 >100 失败关闭；
3. `adjust-traffic` API；`autoPromoteWhenFull` 满量晋级并停用其它 CANARY；
4. OpenAPI 1.0.33；`MultiSlotCanaryAutoPromotePostgresIT`。

## 明确未实现

指标驱动自动晋级、按区域槽位、动态调参 UI。
