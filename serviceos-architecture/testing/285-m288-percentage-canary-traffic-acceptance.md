---
title: M288 CANARY 百分比流量灰度验收矩阵
status: Implemented
milestone: M288
---

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M288-01 | trafficPercent=0 | 全 STABLE | PASS |
| M288-02 | trafficPercent=100 | 全 CANARY | PASS |
| M288-03 | sticky routingKey | 同 key 稳定 | PASS |
| M288-04 | preferCanary | 强制 CANARY | PASS |
